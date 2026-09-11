(ns poultry.store
  "SSoT for the ISCO-08 6122 independent poultry operations actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a coop-monitoring robot performs feed
  dispensing, egg collection and health sensing under this
  advisor/governor pair, which never dispatches hardware itself).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    coop   — a registered coop/flock {:coop-id :client-id :name
             :bird-count number :max-feed-kg-per-bird number}.
             `:max-feed-kg-per-bird` is the registered daily feed
             ceiling per bird — overfeeding is arithmetic, not animal
             welfare judgement, so the governor divides the proposed
             total feed by the registered bird count and checks the
             per-bird result against this ceiling.
    record — a committed operating record (approved feed dispense) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (coop [s coop-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-coop! [s c])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (coop [_ coop-id] (get-in @a [:coops coop-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-coop! [s c]
    (swap! a assoc-in [:coops (:coop-id c)] c) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :coops {} :records [] :ledger []}
                                   seed)))))
