// this script allows executing RESTHeart with GraalVM node
// requires GraalVM installed and properly configured
// example
// $ node --jvm --vm.cp=core/target/restheart.jar core/bin/restheart.js core/etc/test/restheart.yml

const { Worker } = require('worker_threads');

const nqueue = Java.type('org.restheart.polyglot.NodeQueue').instance();
nqueue.setAsRunningOnNode();

const RuntimeException = Java.type('java.lang.RuntimeException');
const util = require('util');
const queue = nqueue.queue();

// handle uncaughtException
process.on('uncaughtException', function (err) {
    console.log('Caught exception from node plugin: ' + err);
});

function JavaToJSNotifier() {
    this.queue = queue;
    this.worker = new Worker(`
        const { workerData, parentPort } = require('worker_threads');
        while (true) {
          // block the worker waiting for the next notification from Java
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
        const code = n[1];
        const request = n[2];
        const response = n[3];
        const out = n[4];

        try {
            const result = Promise.resolve(eval(code).handle(request, response));

            // Will resolve after 10secs
            const timeout = new Promise((resolve, reject) => setTimeout(function() { reject('timeout'); }, 10*1000) );

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

//process.stdin.resume();