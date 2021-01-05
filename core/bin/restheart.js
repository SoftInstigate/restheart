// this script allows executing RESTHeart with GraalVM node
// requires GraalVM installed and properly configured
// example:
// $ node --jvm --vm.cp=core/target/restheart.jar core/bin/restheart.js core/etc/test/restheart.yml

const { Worker } = require('worker_threads');

const nqueue = Java.type('org.restheart.polyglot.NodeQueue').instance();
// set NodeQueue.asRunningOnNode=true to run js plugins with NodeService
nqueue.setAsRunningOnNode();

const RuntimeException = Java.type('java.lang.RuntimeException');
const queue = nqueue.queue();

/**
 * simple cache to save executing eval(code)
 */
class EvalCache {
    constructor(minutesToLive = 10) {
      this.millisecondsToLive = minutesToLive * 60 * 1000;
      this.cache = new Map();
      this.get = this.get.bind(this);
      this.put = this.put.bind(this);
      this.gc = this.gc.bind(this);
      this.fetchDate = new Date(0);
    }

    isCacheExpired() {
      return (this.fetchDate.getTime() + this.millisecondsToLive) < new Date().getTime();
    }

    get(codeHash) {
      if (!this.cache.has(codeHash)) {
        return null;
      } else {
        const ci = this.cache.get(codeHash);
        if ((Date.now() - ci.timestamp) > this.millisecondsToLive) {
            this.cache.delete(codeHash);
            return null;
        } else {
            return ci.value;
        }
      }
    }

    put(codeHash, value) {
        this.cache.set(codeHash, {timestamp: Date.now(), value: value});
    }

    gc() {
        const now = Date.now();
        this.cache.forEach((ci, key, cache) => {
            if ((now - ci.timestamp) > this.millisecondsToLive) {
                cache.delete(key);
            }
        });
    }
}

const CACHE = new EvalCache(60);

// handle uncaughtException
process.on('uncaughtException', function (err) {
    console.log('Caught exception from node plugin: ' + err);
});

function JavaToJSNotifier() {
    this.queue = queue;
    this.worker = new Worker(`
        const { workerData, parentPort } = require('worker_threads');
        while (true) {
          // block the worker waiting for the next notification from RESTHeart
          var data = workerData.queue.take();
          // notify the main event loop that we got new data
          parentPort.postMessage(data);
        }`,
        { eval: true, workerData: { queue: this.queue } });
}

// register a callback to be executed when something is added to the shared queue
const asyncJavaEvents = new JavaToJSNotifier();

asyncJavaEvents.worker.on('message', (n) => {
    const type = n[0];

    if (type === 'parse') {
        const code = n[1];
        const out = n[2];

        const parsed = eval(code);

        if (typeof parsed.handle === "function") {
            const ret = {};
            ret.options = parsed.options ? parsed.options : {};
            ret.handle = 'function';
            out.offer(JSON.stringify(ret));
        } else {
            const ret = {};
            ret.options = parsed.options ? parsed.options : {};
            out.offer(JSON.stringify(ret));
        }
    } else if (type === 'handle') {
        const codeHash = n[1];
        const code = n[2];
        const request = n[3];
        const response = n[4];
        const out = n[5];

        try {
            CACHE.gc();
            let evaluated = CACHE.get(codeHash);

            if (evaluated === null) {
                evaluated = eval(code);
                CACHE.put(codeHash, evaluated);
            }

            const result = Promise.resolve(evaluated.handle(request, response));

            // timeout 60 seconds
            const timeout = new Promise((resolve, reject) => setTimeout(function() { reject('timeout'); }, 60*1000) );

            Promise.race([ timeout, result ]).then(() => {
                out.offer('done');
            }).catch(error => out.offer(new RuntimeException("Error " + error)));
        } catch (error) {
            out.offer(new RuntimeException("Error " + error));
        }
    }
});

// start RESTHeart

const Bootstrapper = Java.type('org.restheart.Bootstrapper');

process.argv.shift();
process.argv.shift();

Bootstrapper.main(process.argv);