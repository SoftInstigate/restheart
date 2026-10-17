# Chunking per bucket: il chunker spezza, l'auto-embedding calcola i vettori

**Status:** Approvata il 2026-09-24 e implementata, da verificare con la build. Issue restheart#754, milestone 9.9.0.
**Date:** 2026-09-24
**Related:** `DocumentChunkingInterceptor`, `AutoEmbeddingInterceptor`, `CollectionEmbeddingConfig`,
`VoyageContextualEmbeddingProvider`, `InProcessDispatcher`; restheart#752 (regole di embedding in
lista), restheart#753 (`$vectorize` sceglie la regola).

## Il principio

Il chunker spezza i file in chunk e li salva in una collection. Non calcola vettori.

I vettori li calcola l'auto-embedding, con le regole `vectorSearch` della collection dei chunk, come
per ogni altra collection. Una sola logica di embedding, configurata in un solo posto: la collection
dove i vettori stanno, che è anche quella dove si cerca.

## Cosa si configura, e dove

Un bucket ha una o più regole di chunking, ognuna con il suo filtro: alcuni file vanno in una
collection, altri in un'altra, e ogni collection ha il suo modello di embedding.

**Sul bucket**, nei metadati: quali file spezzare, come, e dove mettere i chunk.

```json
PATCH /mydb/docs.files
{ "chunking": [
    { "name": "manuals",
      "filter": { "contentType": ["application/pdf", "application/vnd.openxmlformats-officedocument.*"] },
      "target-collection": "manuals_chunks",
      "chunk-size": 1000, "chunk-overlap": 200 },
    { "name": "code",
      "filter": { "extension": [".java", ".ts", ".py"] },
      "target-collection": "code_chunks",
      "splitter": "text" } ] }
```

**Su ogni collection dei chunk**: con quale modello calcolare i vettori. Qui `manuals_chunks`
con il modello contestuale; `code_chunks` può averne un altro.

```json
PATCH /mydb/manuals_chunks
{ "vectorSearch": [
    { "textField": "text", "embeddingField": "vector",
      "provider": "voyageContextualEmbeddingProvider", "model": "voyage-context-4",
      "groupBy": "fileId" } ] }
```

Una regola di chunking non ha `provider`, `model` né `dimensions`. Se li contiene, il controllo dei
metadati la rifiuta, e il messaggio dice di metterli nella regola `vectorSearch` della collection
di destinazione.

## La regola di chunking

| Chiave | Obbligatoria | Significato |
|---|---|---|
| `name` | no | etichetta, scritta su ogni chunk come `rule` |
| `filter` | no | a quali file si applica; senza filtro, a tutti |
| `target-collection` | sì | dove vanno i chunk |
| `chunk-size` | no | caratteri per chunk; altrimenti la config del nodo, poi 1000 |
| `chunk-overlap` | no | caratteri ripresi dal chunk precedente; altrimenti la config del nodo, poi 200 |
| `splitter` | no | `auto`, `text` o `code`, sotto |

**Il filtro** ha tre chiavi, tutte facoltative, senza distinzione di maiuscole. Quando sono più di
una devono combaciare tutte.

- `contentType`: il tipo che Tika rileva dal file; un `*` finale vale per qualunque suffisso.
- `extension`: del nome del file caricato, con o senza punto.
- `metadata`: un filtro MongoDB sul `metadata` del file, che l'applicazione imposta all'upload,
  per esempio `{ "kind": "manual" }`. Non ammette `$where`, `$function`, `$accumulator`.

**La prima regola che combacia**, nell'ordine della lista, è quella applicata, come in una ACL. Un
file che non combacia con nessuna non viene spezzato, e Tika non lo legge nemmeno.

**Lo splitter:**

- `auto`, il default: il codice sorgente riconosciuto si taglia a confini di funzione e classe, il
  resto in finestre di caratteri.
- `text`: sempre finestre di caratteri, anche per il codice, per un modello che lo tratta meglio
  così.
- `code`: confini di funzione e classe ogni volta che il linguaggio è riconosciuto.

Il codice ha overlap zero, a meno che la regola non imposti `chunk-overlap`.

## Il chunk

Ogni chunk porta con sé il file da cui viene, così una ricerca può restringere per campi
dell'applicazione, con un `$match` prima di `$vectorScan` o nel `filter` di `$vectorSearch`.

```json
{ "_id": ObjectId,
  "source": "mydb/docs.files/<fileId>",
  "fileId": <id del file>,
  "chunkIndex": 0,
  "text": "…",
  "filename": "manuale.pdf",
  "contentType": "application/pdf",
  "metadata": { …il metadata del file, copiato… },
  "rule": "manuals",
  "vector": [ … ] }
```

`vector` lo scrive l'auto-embedding, non il chunker, e solo se la collection ha una regola su `text`.

## Il flusso di un upload

1. Il file viene salvato nel bucket, come sempre.
2. Il chunker legge le regole del bucket e cerca la prima che combacia. Nessuna: fine.
3. Estrae il testo con Tika e lo spezza con lo splitter e le misure della regola.
4. Compone i chunk, con i metadati del file.
5. Li scrive nella collection di destinazione con **una sola richiesta**, un `POST` di tutti i chunk
   del file, che passa dalla pipeline di RESTHeart in-process (`InProcessDispatcher`) con i
   parametri attaccati alla richiesta dell'upload. La collection, se non esiste, viene creata vuota.
6. Su quella richiesta gira l'auto-embedding della collection dei chunk. Con `"groupBy": "fileId"`
   e un modello contestuale, calcola i vettori di tutti i chunk del file insieme, come un solo
   documento.
7. La risposta all'upload riporta i warning della scrittura dei chunk: un embedding fallito, una
   scrittura rifiutata.

**Perché una sola richiesta.** Un solo `POST` con tutti i chunk del file è intercettato una volta
dall'auto-embedding, che li vede insieme, li raggruppa per `fileId` e li embedda in una chiamata.

**Perché dalla pipeline.** Così sui chunk girano l'auto-embedding, i controlli dei metadati e, in un
deployment multi-tenant, gli override del tenant, come per ogni scrittura.

**Con quale identità.** Se il bucket prevede il chunking, il chunking si fa. Chi carica il file deve
poter scrivere nel bucket, non nella collection dei chunk. Le scritture del chunker passano quindi
con un'identità interna: una regola registrata in `ACLRegistry` autorizza solo le richieste
in-process marcate dal chunker, un segno che il dispatcher mette sull'exchange e che non può
arrivare dalla rete. Nessuna credenziale da custodire o revocare.

## `groupBy` nelle regole di embedding

Un'opzione nuova della regola `vectorSearch`, utile anche fuori dal chunking.

- I documenti **di una stessa scrittura** con lo stesso valore del campo `groupBy` vengono embeddati
  insieme, come un solo documento, da un modello contestuale: ogni vettore tiene conto degli altri
  del gruppo.
- Senza `groupBy`, o con un modello non contestuale, ogni documento è embeddato da solo, come oggi.
- Il raggruppamento avviene dentro una scrittura. Documenti dello stesso gruppo scritti in richieste
  diverse non si vedono tra loro.

Esempio fuori dal chunking: un'applicazione che scrive in blocco i paragrafi di un articolo, ognuno
con lo stesso `articleId`.

## Il limite del modello contestuale di Voyage

Dalla documentazione di Voyage, verificata il 2026-09-24:

| Limite | Valore |
|---|---|
| Token per documento, cioè per gruppo, con `voyage-context-4` | 32.000 |
| Token per richiesta | 120.000 |
| Chunk per richiesta | 16.000 |
| Documenti per richiesta | 1.000 |

Oggi un gruppo oltre i 32.000 token fa fallire la chiamata, e tutti i chunk del file restano senza
vettore, con un warning.

**Il comportamento proposto**, dentro `VoyageContextualEmbeddingProvider`, senza provider nuovi:

1. Un gruppo sotto il limite parte com'è: un file piccolo, un array, ogni chunk vede tutti gli altri.
2. Un gruppo oltre il limite si divide in finestre consecutive sotto i 32.000 token. Ogni chunk vede
   i chunk della sua finestra, non quelli delle altre.
3. Più finestre vanno nella stessa richiesta, fino a 120.000 token e 16.000 chunk; oltre, più
   richieste.
4. I token si stimano dai caratteri, per eccesso, perché non abbiamo il tokenizer di Voyage.
5. Se una richiesta fallisce, i chunk delle richieste riuscite hanno il vettore e gli altri no; il
   warning dice quanti sono rimasti senza.

Esempio: un manuale da 300 pagine, 600 chunk da 1.000 caratteri. Con la stima prudente, tre
caratteri per token, un chunk vale circa 334 token e una finestra da 30.000 ne tiene 89: 7 finestre,
in 3 richieste da 110.000 token. Tutti i 600 chunk hanno un vettore, ognuno con il contesto di circa
45 pagine intorno.

## Attivazione

- **Solo i bucket con `chunking` nei metadati vengono spezzati.** Un bucket senza non viene mai
  toccato, qualunque sia la config del nodo. Una lista vuota equivale a nessuna regola.
- Niente `apply-to-all`: `restheart-ai` non è mai uscito in una release, quindi non c'è un
  comportamento di prima da conservare.
- La config del nodo tiene solo i default di `chunk-size` e `chunk-overlap`. Perde
  `target-collection`, che ogni regola dichiara, ed `embedding-provider`.

## Controllo dei metadati

Un `PUT` o `PATCH` di un bucket con regole non valide riceve `400`, con il nome della regola, per
esempio `chunking[1]`:

- `target-collection` mancante;
- una chiave sconosciuta nella regola o nel filtro, compresi `provider`, `model`, `dimensions`, con
  l'indicazione di metterli nella regola `vectorSearch` della collection di destinazione;
- un valore del tipo sbagliato; `chunk-overlap` non minore di `chunk-size`;
- `$where`, `$function`, `$accumulator` nel filtro `metadata`;
- `splitter` diverso da `auto`, `text`, `code`.

`groupBy` nella regola `vectorSearch`: una stringa non vuota, controllata da
`vectorSearchMetadataChecker`.

## La ricerca

`$vectorize` su `manuals_chunks` legge la regola `vectorSearch` di quella collection, quindi calcola
il vettore della domanda con lo stesso modello dei chunk (restheart#753). Non serve altro.

## Il ciclo di vita dei chunk

- **Un file sostituito con `PUT`**: prima di scrivere i chunk nuovi, il chunker cancella quelli del
  file. Li cerca in tutte le collection di destinazione delle regole del bucket, perché la regola
  che combacia ora può non essere quella di prima.
- **Un file cancellato**: i suoi chunk vengono cancellati, nelle stesse collection.
- **Una cancellazione in blocco** di file, `DELETE /bucket.files/*?filter=…`: prima della
  cancellazione il chunker legge gli id dei file che il filtro colpisce, dopo cancella i loro chunk.
- I chunk di un file si riconoscono da `source`, che include database, bucket e id: due bucket con
  file dallo stesso id non si confondono.

## Decisioni del 2026-09-24

1. **Identità:** se il chunking è previsto, si fa. Chi carica deve poter scrivere nel bucket, non
   nella collection dei chunk; il chunker scrive con un'identità interna, sezione "Il flusso".
2. **Collection inesistente:** viene creata vuota.
3. **File sostituito:** i chunk vecchi vengono cancellati.
4. **File cancellato:** i suoi chunk vengono cancellati.
5. **`groupBy`:** il nome va bene.
6. **Attivazione:** senza `chunking` nei metadati, nessun chunking; niente `apply-to-all`.

## Criteri di accettazione

- Su un bucket con le regole `manuals` e `code`: un PDF produce chunk in `manuals_chunks`, un `.java`
  in `code_chunks`, un `.png` nulla.
- I chunk portano `filename`, `contentType`, `metadata` del file e `rule`.
- Un filtro `metadata` instrada un file per un campo impostato all'upload, e vince se è la prima
  regola.
- Un bucket senza `chunking` non viene mai toccato.
- Un utente che può scrivere nel bucket ma non nella collection dei chunk ottiene comunque i chunk.
- Sostituire un file sostituisce i suoi chunk; cancellarlo, anche in blocco, li cancella.
- Con una regola `vectorSearch` su `text` con `groupBy: fileId` e un modello contestuale, i chunk
  di un file vengono embeddati in una sola chiamata, come un solo documento.
- Senza regola `vectorSearch` sulla collection dei chunk, i chunk si salvano senza vettore.
- Un file oltre i 32.000 token ha tutti i chunk con il vettore, in finestre.
- Regole non valide, comprese quelle con `provider` o `model`, ricevono `400` sulla scrittura dei
  metadati.
- La suite karate copre i casi sopra con provider finti, uno dei quali contestuale.
- Docs: `ai/vector-search.adoc`, sezione "Chunk documents for RAG".

## Lo stato del codice

Implementata nel working tree di `restheart`, non ancora compilata:

- `BucketChunkingConfig` e `ChunkingMetadataChecker`: le regole del bucket e il loro controllo.
- `DocumentChunkingInterceptor`: spezza, scrive con un `POST` in-process, cancella i chunk di un file
  sostituito o cancellato; `ChunkedFilesDeleteCollector` per le cancellazioni in blocco.
- `RuleEmbedder`: la logica di embedding, con `groupBy`, usata da `AutoEmbeddingInterceptor`.
- `VoyageContextualEmbeddingProvider`: finestre e richieste entro i limiti di Voyage.
- Test unitari e `ai/chunking-rules.feature` con provider finti, uno contestuale.
