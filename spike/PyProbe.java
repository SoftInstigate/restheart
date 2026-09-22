import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;
import java.nio.file.*;
import java.util.*;

/** Standalone probe: same Context settings as ContextQueue.newContext, runs a python file or snippet. */
public class PyProbe {
    public static void main(String[] a) throws Exception {
        var opts = new HashMap<String, String>();
        for (int i = 1; i < a.length; i++) { var kv = a[i].split("=", 2); opts.put(kv[0], kv[1]); }
        long t0 = System.nanoTime();
        var engine = Engine.create();
        long t1 = System.nanoTime();
        try (var ctx = Context.newBuilder().engine(engine)
                .allowAllAccess(true).allowHostAccess(HostAccess.ALL).allowHostClassLookup(c -> true)
                .allowIO(IOAccess.ALL).allowExperimentalOptions(true).allowValueSharing(true)
                .options(opts).build()) {
            long t2 = System.nanoTime();
            var code = Files.exists(Path.of(a[0])) ? Files.readString(Path.of(a[0])) : a[0];
            try {
                var v = ctx.eval(Source.newBuilder("python", code, "probe").build());
                System.out.println("RESULT: " + v);
            } catch (PolyglotException pe) {
                System.out.println("POLYGLOT EXCEPTION: " + pe.getMessage() + " guest=" + pe.isGuestException() + " internal=" + pe.isInternalError());
                pe.printStackTrace(System.out);
            }
            long t3 = System.nanoTime();
            System.out.printf("TIMING engine=%dms context=%dms eval=%dms%n", (t1-t0)/1_000_000, (t2-t1)/1_000_000, (t3-t2)/1_000_000);
        }
    }
}
