# Spike: Python plugins for RESTHeart via GraalPy

Branch `spike/graalpy`, based on upstream `ca813b8eb` (master, 10.0.0-SNAPSHOT).

Goal: can RESTHeart get Python plugins that behave like its existing JavaScript
plugins? This is a feasibility report plus a minimal proof of concept, not an
implementation.

**Short answer: yes, and less work than expected for the plumbing.** A Python
service and a Python interceptor now deploy from the same `package.json` as JS
ones and answer real requests, sharing the deployer, the context pool, the
plugin registry and the JS interceptor classes; ~370 lines of new Java plus
~25 lines changed in existing files. The hard parts are not the wiring: they
are **per-plugin package installation**, **C-extension packages across a pool of
contexts**, and **Python context startup cost** (~20 s for the first context in
this environment).

Everything below marked as a measurement was run; anything not run is marked
**unverified**.

---

## 0. Environment, and one blocker worth stating first

| | |
|---|---|
| Host | macOS 15.7.9, Intel Core i5-1030NG7 (4 cores / 8 threads, 1.1 GHz), 8 GB RAM |
| Build JDK | GraalVM CE 25.0.1 (macOS x64), local to the spike dir; Maven 3.9.12 via `mvnw`, repo local to the spike dir |
| Runtime | `softinstigate/graalvm:25.1.3-graalce` (linux/amd64) — the image `core/Dockerfile` ships on — GraalVM CE 25.1.3 / JDK 25.0.3 |
| Docker VM | 8 CPUs, 3.82 GiB RAM |
| MongoDB | `mongo:8.2` container (8.0 refuses to start on this Docker VM kernel, SERVER-121912) |

**GraalVM 25.1.x no longer ships macOS x86_64.** `python-resources-25.1.3.jar`
contains natives only for `darwin/aarch64`, `linux/{amd64,aarch64}` and
`windows/amd64`, so on an Intel Mac GraalPy dies during context init:

```
NoSuchFileException: META-INF/resources/darwin/amd64/native.sha256
  at com.oracle.graal.python.resources.PythonResource.versionHash(PythonResource.java:251)
```

The same gap hits JS on that platform, though less visibly: `truffle-api-25.1.3.jar`
has no `darwin/amd64` truffleattach either, so an Intel Mac silently runs
**every** polyglot plugin in the Truffle fallback runtime (interpreter only):

```
[engine] WARNING: The polyglot engine uses a fallback runtime that does not support runtime compilation
```

So all Python runs below happen in the linux/amd64 container. That is also the
realistic target (RESTHeart ships Linux images), but it means **Intel-Mac
developers cannot run Python plugins locally at all**, and their JS plugins run
without the JIT. Apple Silicon and Linux are unaffected.

---

## 1. How JavaScript plugins work today

References are to upstream `ca813b8eb`.

### Discovery and deployment

* `PolyglotDeployer` (`polyglot/src/main/java/org/restheart/polyglot/PolyglotDeployer.java:83`)
  is a `@RegisterPlugin`-annotated `Initializer`, enabled by default, with
  `registry`, `rh-config` and an optional `mclient` injected (`:89-96`).
* `@OnInit onInit()` (`:101`) bails out unless `Version.getCurrent().isRelease()`
  (`isRunningOnGraalVM()`, `:118`), then resolves the plugins directory from
  `/core/plugins-directory` (`:213-216`), deploys everything found, and starts a
  `DirectoryWatcher` (`:157`) for hot (re)deploy of `.mjs` files.
* A plugin is **a directory containing `package.json`**. The deployer reads three
  array properties (`findDeclaredPlugins`, `:322`): `rh:services` (`:303`),
  `rh:node-services` (`:311`, experimental Node mode) and `rh:interceptors`
  (`:319`). Each entry is a path to a `.mjs` file relative to the plugin dir.
* Services are registered with `registry.plugService(record, uri, matchPolicy, secured)`
  (`:406`), interceptors with `registry.addInterceptor(record)` (`:464`), and both
  are tracked in a static `DEPLOYEES` map for undeploy (`:473-513`).
* `Source.findLanguage()` is deliberately *not* called anywhere; comments at
  `:348-352` tie this to Truffle bug oracle/graal#7520.

### The plugin contract the JS code sees

A service module exports `options` and `handle`:

```js
export const options = { name, description, uri, secured, matchPolicy }   // test-js-plugins/hello.mjs
export function handle(req, res) { ... }
```

An interceptor module exports `options` (with `interceptPoint`, `pluginClass`)
plus `handle` and `resolve`. Both are read by evaluating a synthesised ES module
import against the plugin file — `"import { options } from '<uri>'; options;"`
(`JSStringService.java:110`, `:148`; `JSInterceptorFactory.java:110`, `:200`, `:219`) —
and then validated field by field (`JSStringService.checkOptions`, `:188`;
`JSInterceptorFactory:122-235`), with long error hints shown to the plugin author.

`options.pluginClass` selects which typed interceptor wrapper is instantiated
(`JSInterceptorFactory:247-338`): `StringInterceptor`, `BsonInterceptor`,
`ByteArrayInterceptor`, `ByteArrayProxyInterceptor`, `CsvInterceptor`,
`JsonInterceptor`, `MongoInterceptor`, `WildCardJSInterceptor` — thin subclasses
of `JSInterceptor<R, S>` that only fix the generic request/response types
(e.g. `MongoJSInterceptor`).

Inside the guest code, the API is **the RESTHeart Java objects themselves**: the
`Request`/`Response` instances are passed straight into the guest function
(`JSStringService:182-185`, `JSInterceptor:80-96`), the context is built with
`allowAllAccess(true)`, `HostAccess.ALL`, `allowHostClassLookup(c -> true)`,
`IOAccess.ALL` (`ContextQueue.java:272-280`), so `Java.type(...)` reaches any
class, and three globals are injected (`ContextQueue.addBindings`, `:293-309`):
`LOGGER` (slf4j), `mclient` (the `MongoClient`, only if present) and `pluginArgs`
(that plugin's section of the RESTHeart config, by plugin name).

npm packages: if the plugin root has a `node_modules` directory, the deployer
enables CommonJS `require` for that plugin's contexts (`JSStringService:90-95`,
`JSInterceptorFactory:85-91`). `options.modulesReplacements` is parsed but is now
a no-op — the underlying GraalJS option was removed in 25.1 (`ContextQueue:267-270`).

### Execution model

* **One `Engine` for the whole process**, lazily created (`JSPlugin.engine()`,
  `JSPlugin.java:47-65`), shared by services and interceptors
  (`JSInterceptorFactory:64-71` explicitly reuses it: two Engines corrupt Truffle
  bookkeeping).
* **One `ContextQueue` per plugin** (`JSPlugin:100`), pre-populating a pool of
  `1 × availableProcessors` contexts (`ContextQueue.java:64`, `:108`) — note the
  class javadoc still says "4 × CPU cores" (`:45`), a doc/code mismatch.
* Every polyglot operation — engine creation, context creation, `enter`/`leave`,
  `eval`, `close` — is funnelled onto **one dedicated platform thread**
  (`PolyglotThreadUtils`, `:74` `Thread.ofPlatform().name("RH JS PLT", 0)`), a
  workaround for oracle/graal#7520 that can be turned off with
  `-Drestheart.polyglot.force-platform-threads=false`. So all guest code in the
  process is serialised on a single thread, whatever the pool size.
* Per request, `executeWithContext` (`ContextQueue:165`) takes a context from the
  pool, enters it, runs, leaves, returns it. The evaluated `handle`/`resolve`
  functions are cached **per context** in its bindings (`cacheHandleFunction`,
  `:232`; `cacheResolveFunction`, `:251`), so each context pays the module import
  once.

---

## 2. GraalPy against that design

### Version and packaging

`pom.xml` pins `graalvm.version`/`truffle.version` = **25.1.3** (`pom.xml:58-59`)
and pulls GraalJS as `org.graalvm.js:js-language` rather than the
`js-community` aggregate (`pom.xml:496-501`, comment cites issue #510). GraalPy
has an exactly matching release: `org.graalvm.python:python-language:25.1.3` plus
`org.graalvm.python:python-resources:25.1.3` (CPython 3.12.8 level), declared the
same way. That is what this spike uses, and it built and ran with no version
juggling. Sizes: `python-language` 95 MB, `python-resources` 14 MB — the polyglot
module's `target/lib` goes from 65 MB to 170 MB, and `core/target/plugins/lib`
from 139 MB to 258 MB (the two GraalPy jars are 118 MB). That lands in the Docker image and the `.tar.gz` dist.

### Context and engine

No new machinery needed: the same `Engine` hosts both languages, and a Python
plugin gets its own `ContextQueue` exactly like a JS plugin. The only
language-specific bits are which bindings to write and which Source to evaluate.
GraalPy is happy on the single "RH JS PLT" platform thread; in fact GraalPy is
more thread-sensitive than GraalJS (it holds a GIL), so the existing
serialisation is convenient, at the cost of using one core for all guest code.

One real difference: **`python` bindings are `__main__`'s globals**, not a shared
global object, so values written there are invisible to an imported plugin
module. To give Python plugins the same `LOGGER` / `mclient` / `pluginArgs`
globals that JS plugins have, this spike writes them into the bindings and then
copies them into `builtins` (`ContextQueue.addBindings(..., language)`).

### Packages, per plugin

The JS side keys off `node_modules`. The Python equivalent is a **virtualenv per
plugin directory**: if `<plugin>/.venv/pyvenv.cfg` exists, the spike sets
`python.Executable` to `<plugin>/.venv/bin/python` and `python.ForceImportSite=true`,
which gives each plugin its own isolated `site-packages`. This works (verified,
§4) and is the documented GraalPy embedding pattern.

Creating those venvs needs a GraalPy launcher. There is no GraalPy standalone for
this platform, so the spike drives `-m venv` / `pip` through the **JVM launcher**
(`org.graalvm.python:python-launcher`, same 25.1.3) inside RESTHeart's base image
(`spike/make-venvs.sh`, `spike/pylauncher/`). It works but is slow: **~5.5–9 min
per venv** here (GraalPy interpreted on the JVM, plus Docker's slow bind mounts on
macOS). For production this belongs in a build step (a `graalpy-maven-plugin`
goal, or a documented `graalpy -m venv` for plugin authors), never at deploy time.

---

## 3. The proof of concept

### What changed (`git diff ca813b8eb..spike/graalpy`, 13 files, +523/−13)

| File | Change |
|---|---|
| `pom.xml`, `polyglot/pom.xml` | add `python-language` + `python-resources` at `${truffle.version}` |
| `ContextQueue.java` | language-aware: `language` field (default `"js"`), bindings via `ctx.getBindings(language)`, `cacheHandle/ResolveFunction` use `source.getLanguage()`, `addBindings(..., language)` also exports globals to Python `builtins` |
| `JSPlugin.java` | derives the context language from `handleSource.getLanguage()` |
| `PolyglotDeployer.java` | dispatches `.py` entries to the Python service/interceptor factory; watcher also reacts to `.py` |
| `python/PyPlugins.java` (new) | plugin-root lookup, venv detection, `rh:python-options`, the module-loading `Source`s, `options` dict → Java map |
| `python/PyStringService.java` (new) | Python counterpart of `JSStringService` |
| `python/PyInterceptorFactory.java` (new) | Python counterpart of `JSInterceptorFactory`, reusing the existing `*JSInterceptor` classes |
| `polyglot/src/test/resources/test-py-plugins/` | `hello.py` + `hello_interceptor.py` + `package.json`, mirroring `test-js-plugins` |
| `spike/` | run/measure/venv scripts, probes, raw results |

Declaration is deliberately identical to JS — the same file, the same keys:

```json
{ "rh:services": ["hello.py"], "rh:interceptors": ["hello_interceptor.py"] }
```

```python
options = {"name": "pyHelloWorldService", "description": "...",
           "uri": "/pyhello", "secured": False, "matchPolicy": "EXACT"}

def handle(req, res):
    if req.isGet():
        res.setContent(json.dumps({"msg": "Hello World!"}))
        res.setContentTypeAsJson()
```

The interceptor mirrors the JS one, including `resolve(req)` using
`req.isHandledBy("pyHelloWorldService")`.

The loading trick: instead of JS's synthesised `import { x } from '<file>'`, a
Python `Source` imports the plugin file once per context via
`importlib.util.spec_from_file_location`, caches it in `sys.modules`, and
evaluates to one member (`options`, `handle` or `resolve`). Everything
downstream — the pool, the per-context function cache, `JSInterceptor.handle`
/`resolve`, the registry records — is unchanged.

### It answers a real request

```
$ curl localhost:8080/pyhello
{"msg": "Hello World!", "note": "modified by pyHelloWorldInterceptor"}
$ curl localhost:8080/hello          # the JS pair, unchanged
{"msg":"Hello World!","note":"modified by helloWorldInterceptor"}
```

Log lines are the normal ones:

```
Service 'pyHelloWorldService' deployed at URI '/pyhello' ... Uri match policy: EXACT
Interceptor 'pyHelloWorldInterceptor' deployed with description: 'modifies the response of pyHelloWorldService'
```

The 28 existing `polyglot` unit tests still pass (`./mvnw test -pl polyglot`).

Reproduce: `spike/run-rh.sh rh -s`, then `spike/measure.sh`, `spike/make-venvs.sh`,
`spike/packages-test.sh` (all read `../env.sh` for the local JDK/Maven).

---

## 4. Packages: what loads, what fails, why

Five probe plugins, each with its own venv, deployed together
(`spike/py-plugins/`, raw JSON in `spike/results/packages/`).

| Case | Result |
|---|---|
| **stdlib** (34 modules incl. `sqlite3`, `ssl`, `asyncio`, `ctypes`, `multiprocessing`, `zoneinfo`, `tomllib`, `pip`, `venv`) | **all 34 import**. `sqlite3` in-memory query, threads, `hashlib`, `zlib`, `decimal` all correct |
| Java interop from Python | works: `java.type("org.bson.BsonDocument").parse('{"a":1}')` |
| `LOGGER` / `pluginArgs` globals | present and usable inside the plugin module |
| **pure-Python package** (`python-dateutil` 2.9 + `six`) | **works**, `parser.parse` + `relativedelta` correct; pip even applies "GraalPy patches" where it has them |
| **pydantic** 2.13.5 / `pydantic-core` 2.46.5 (Rust native) | **binary wheel exists and installs** (`pydantic_core-2.46.5-graalpy312-graalpy250_312_native-manylinux…whl`). Import then hits the multi-context limit (below); with the fixes applied it **works**: validation and a real `int_parsing` error |
| **numpy** | `pip install numpy` picks 2.4.4, for which **no GraalPy wheel exists**, and falls back to a source build that fails: `ERROR: Unknown compiler(s): cc, gcc, clang…` (the runtime image has no toolchain, and GraalPy also warns it needs the `patch` utility). Pinning **`numpy==2.2.4`** gets the wheel from `https://www.graalvm.org/python/wheels/` and installs in ~9 min. With the fixes below it **works**: `mean` over a 1M array and `linalg.det` of a 200×200 matrix, numpy 2.2.4 |
| `subprocess.run(["echo"])` | fails — the distroless runtime image has no `echo` binary. Environment, not GraalPy |

### The C-extension blocker, and what it costs

Out of the box, the first request to the numpy/pydantic plugins returned:

```
SystemError: Option python.IsolateNativeModules is set to 'false' and a second GraalPy
context attempted to load a native module 'numpy._core._multiarray_umath' ...
```

This is the core tension with the current design: **RESTHeart creates a pool of
contexts per plugin** (8 here), and a native extension may be loaded by only one
context per process unless isolation is enabled. Getting numpy and pydantic to
work needed all three of:

1. `python.IsolateNativeModules=true` — and its own docs say *every* context in
   the process must set it, so this has to be a **global** RESTHeart setting, not
   per plugin (this spike exposes it per plugin via `rh:python-options` only to
   test it);
2. **`patchelf`** available (installed into the venv with `pip install patchelf`);
   without it: `SystemError: Could not find 'patchelf'. IsolateNativeModules option needs 'patchelf'`;
3. a base image with **`libgcc_s.so.1`** — on `softinstigate/graalvm:25.1.3-graalce`
   (distroless) `pydantic_core…so` fails with
   `libgcc_s.so.1: cannot open shared object file`; on the `-shell` (Debian) variant
   of the same image both packages import and run.

Also worth recording: GraalPy binary wheels are ABI-tagged per GraalPy release
(`graalpy250_312`, `graalpy253_313`, …). There is no `graalpy251_312` tag, yet the
25.0-tagged wheels install and run on 25.1.3 — so the pinned GraalVM version and
the available wheel set are coupled, and a version bump can silently turn a
working plugin into a 10-minute source build (or a failure).

---

## 5. Rough measurements

Same container, same jars, plugins deployed one language at a time, standalone
mode (`-s`, no MongoDB), warm GraalPy resource cache, load driven from the host
over the Docker port. **Caveat:** Docker Desktop on this old Intel Mac adds ~4–5 ms
to every request and the VM has 3.8 GiB, so absolute numbers are pessimistic;
compare the columns, not the values. Two runs each, both shown.

### Startup and deploy (seconds)

| | no plugins | JS | Python |
|---|---|---|---|
| Process start → "RESTHeart started" (run 1 / run 2) | 12.7 / 15.7 | 28.8 / 18.2 | **59.4 / 46.4** |
| Service deploy (last "Found plugin dir" → "deployed") | — | 7.57 / 3.29 | **37.01 / 27.53** |
| Interceptor deploy | — | 0.25 / 0.15 | **4.46 / 3.97** |

With `-XX:ActiveProcessorCount=1`, i.e. a pool of **1** context instead of 8:
Python service deploy **19.89 s**, interceptor 1.51 s (JS: 2.54 s / 0.08 s). So the
first Python context costs ~20 s and the other seven add ~8 s — the dominant cost
is GraalPy context initialisation itself (interpreted, cold JIT), not the pool.
First-ever container start with an empty resource cache was ~36 s for the
service, i.e. unpacking `python-resources` adds ~10 s once per machine.

### Latency (ms)

| | JS run 1 / run 2 | Python run 1 / run 2 |
|---|---|---|
| 1st request | 1493 / 697 | **2910 / 2754** |
| requests 2–8 | 169…31 / 112…27 | **1724, 2146, 1240, 444, 453, 823, 564** (same shape both runs) |
| requests 9–16 | 32…42 / 19…18 | 13.7…15.5 / 15.4…24.4 |
| warm p50 (1000 seq, after 2000 warmup) | 6.72 / 5.14 | 5.82 / 5.54 |
| warm p99 | 16.8 / 10.35 | 14.29 / 14.34 |
| 8-client p50 / p99 | 16.95 / 159.76 — 14.38 / 76.71 | 23.65 / 60.74 — 23.17 / 46.08 |
| 8-client throughput (req/s) | 325.9 / 453.6 | 325.0 / 322.4 |

Two things stand out. **Warm Python is as fast as warm JS** for this workload
(both ~5–7 ms p50, dominated by Docker networking and the single-thread hop).
And **the "first request" penalty is paid once per pooled context**: exactly the
first 8 requests are slow, because each context imports the module on first use.
With a 1-context pool, only the very first request is slow (2.0 s), then ~15 ms.
A production version should warm the contexts at deploy time rather than on the
request path.

### Memory (container RSS)

| | no plugins | JS | Python |
|---|---|---|---|
| after startup (run 1 / run 2) | 221 / 224 MiB | 343 / 352 MiB | **490 / 453 MiB** |
| after 3000 requests | — | 361 / 446 MiB | **530 / 522 MiB** |

Roughly: +130 MiB for a JS plugin pair, **+230 MiB for the Python pair** (8
contexts each). With all five probe plugins plus their venvs loaded, RSS was
~712–754 MiB. On the "1–2 GB container" sizing in `core/Dockerfile`'s comments,
a couple of Python plugins with default pool sizes is a tight fit.

### Native image

**Not attempted** (agreed up front: this machine has 8 GB and the build asks for
`-J-Xmx10g`). What is verifiable from the artifacts: `python-language-25.1.3.jar`
ships `META-INF/native-image/org.graalvm.python/python-language/{native-image.properties,
reflect-config.json,resource-config.json}` plus a `com.oracle.graal.python.capi`
reachability-metadata file, and its build args include
`--initialize-at-build-time=com.oracle.graal.python` and
`-H:MaxRuntimeCompileMethods=20000`, so GraalPy is *designed* to be built into a
native image. **Unverified:** whether RESTHeart's `-Pnative` build succeeds with
python-language on the classpath, what it does to image size and build memory,
and — most doubtful — whether C-extension packages work in the native image
(they need a C API runtime; `IsolateNativeModules`/`patchelf` behaviour there is
untested). Treat native + Python as an open question, and pure-Python-only as the
plausible first target.

---

## 6. Proposed design for a production version

Keep the mirror, and make the language dimension explicit rather than implicit:

1. **Declaration**: same `package.json`, same `rh:services` / `rh:interceptors`
   keys, dispatch on file extension. (`pyproject.toml` could be accepted later;
   it buys nothing now.)
2. **Module contract**: `options` dict + `handle` (+ `resolve`), validated with
   the same field-by-field checks and author-friendly hints as the JS side. The
   JS validation in `JSStringService.checkOptions` / `JSInterceptorFactory`
   should be factored into one language-neutral validator over a `Map`, instead
   of the spike's duplicated-but-smaller version.
3. **Same guest API**: host objects passed through, `LOGGER` / `mclient` /
   `pluginArgs` as globals (via `builtins` for Python), `java.type` for host
   classes. Document `java.type` as the Python spelling of `Java.type`.
4. **Packages**: `<plugin>/.venv` is to Python what `node_modules` is to JS.
   Ship a documented way to create it (`graalpy-maven-plugin`, or a
   `bin/graalpy` wrapper in the distribution), plus a lockfile/`requirements.txt`
   convention. Never install at deploy time.
5. **Native extensions**: set `python.IsolateNativeModules=true` for all Python
   contexts, document the `patchelf` requirement, and add `libgcc` to the runtime
   image — or accept "pure Python only" and fail with a clear message.
6. **Pool sizing**: make the per-plugin pool size configurable (and default it
   lower for Python, e.g. 1–2), and warm each context at deploy time so no
   request pays the module import.
7. **Packaging**: +118 MB of jars is significant for a feature many users will
   not enable. Consider a separate `restheart-polyglot-python` module so
   `python-language` is only present when wanted.

### Work estimate (production-ready, excluding native image)

| Item | Estimate |
|---|---|
| Language-neutral plugin loading + validation refactor (JS and Python share one path), Python service/interceptor types, error messages | 4–6 d |
| Context/pool work: configurable pool size, deploy-time warmup, `IsolateNativeModules` as a global option, lifecycle/undeploy parity, hot redeploy of `.py` | 3–5 d |
| Packaging and tooling: venv convention, `graalpy` wrapper or Maven goal, runtime-image changes (`libgcc`, `patchelf`), dist size decisions | 4–6 d |
| Tests: unit tests mirroring the JS ones, karate integration tests, a package-matrix test (stdlib / pure / native) | 4–6 d |
| Docs and examples (`examples/`, plugin authoring guide, package installation) | 2–3 d |
| Buffer for GraalPy-specific surprises (threading under load, memory tuning, wheel/ABI pinning policy) | 3–5 d |
| **Total** | **≈ 4–6 weeks of focused work** |

Native-image support for Python plugins is deliberately excluded; it needs its
own spike and could be anywhere from "adds a build flag" to "not feasible for
C-extension plugins".

---

## 7. What works / what doesn't / open questions

**Works (verified here)**

* Python service + Python interceptor, declared and deployed exactly like JS ones, answering a real HTTP request, including `resolve()` gating on `isHandledBy`.
* Sharing one `Engine`, the per-plugin `ContextQueue`, the platform-thread serialisation, the per-context function cache, and the existing `*JSInterceptor` classes.
* `LOGGER`, `pluginArgs`, host objects, `java.type` interop from Python.
* **MongoDB from Python**: `mclient.getDatabase("restheart").getCollection("spike")` insert + `find()` round-trip against a real MongoDB 8.2.
* Whole Python stdlib probed (34/34), a pure-Python package, and — with three fixes — numpy and pydantic doing real work.
* Existing JS behaviour and the 28 polyglot unit tests unaffected.

**Doesn't work / costs**

* No macOS x86_64 support at all in GraalVM 25.1.x (and JS there is interpreter-only).
* C extensions need `IsolateNativeModules` + `patchelf` + `libgcc` in the runtime image; the shipped distroless image fails on `libgcc_s.so.1`.
* Python context init ~20 s, ×N pooled contexts; first request per context is seconds.
* +118 MB of jars; +230 MiB RSS for one Python plugin pair at the default pool size.
* Venv creation through the JVM launcher takes minutes; needs to be a build-time step.
* Wheel availability is tied to the exact GraalPy version via ABI tags.

**Open questions**

* Native image with GraalPy: does `-Pnative` still build, at what size/build-memory, and do C extensions survive it? (**unverified**)
* Is the single "RH JS PLT" thread acceptable once Python plugins do real CPU work? Throughput here was ~322 req/s for a trivial handler; a heavier Python handler blocks JS plugins too, since they share the thread.
* Pool sizing policy per language, and whether Python contexts should be lazily created instead of pre-populated.
* Should `pluginArgs`/config lookup, `secured`, `matchPolicy` etc. get a shared validator so JS and Python cannot drift?
* Packaging split (`restheart-polyglot-python`) vs. one fat polyglot module.
* Python venv provenance/security: plugin dirs would now carry third-party binaries; does that change RESTHeart's plugin trust model?

**Pre-existing issues noticed while reading (not introduced here)**

* `JSInterceptorFactory:259-269` maps `"BsonInterceptor"` to `StringJSInterceptor`, while `MongoInterceptor` gets `MongoJSInterceptor`; looks like a copy/paste bug (`BsonJSInterceptor` exists but is never instantiated).
* `ContextQueue`'s javadoc says the pool is "4 × CPU cores" (`:45`); the code uses `1 ×` (`:64`).
* `deployInterceptor` (`PolyglotDeployer:457`) has no try/catch, so a single malformed interceptor throws out of `@OnInit` and aborts startup, whereas a malformed *service* is caught and logged (`:413`). The Python path in this spike wraps guest errors into `IllegalArgumentException` for that reason.
