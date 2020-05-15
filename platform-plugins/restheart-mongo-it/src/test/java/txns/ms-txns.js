s1 = db.getMongo().startSession();
s2 = db.getMongo().startSession();
snt = db.getMongo().startSession();

s1.startTransaction();
s2.startTransaction();

c1 = s1.getDatabase("db").coll;
c2 = s2.getDatabase("db").coll;
cnt = snt.getDatabase("db").coll;

c1.insert({_id: "txn1-1", s: 1 })
cnt.insert({_id: "txn2-1" })