(ns poultry.governor
  "PoultryProductionGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  the robot-dispensed physical work (feed dispensing, egg collection,
  health sensing) an advisor may propose. The governor never
  dispatches hardware itself. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Feed twist: a proposed feed dose divided by
  the registered bird count is arithmetic comparison against the
  registered per-bird ceiling — overfeeding is arithmetic, not animal
  welfare judgement.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose (the
                           governor never dispatches hardware; it only
                           gates what the robot may execute).
    3. coop basis           — a feed approval must cite a REGISTERED
                           coop belonging to this client.
    4. per-bird feed ceiling — (feed-kg / bird-count) must not exceed
                           the coop's registered
                           :max-feed-kg-per-bird (arithmetic, not
                           welfare judgement).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    5. :op :administer-medication (no veterinary medication
                           administration without the governor gate).
    6. :op :approve-outbreak-containment (disease-outbreak
                           containment decisions always require human
                           sign-off).
    7. low confidence (< `confidence-floor`)."
  (:require [poultry.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:administer-medication :approve-outbreak-containment})

(defn- hard-violations [{:keys [request proposal]} client-record c]
  (let [{:keys [op feed-kg]} proposal
        feed? (= :approve-feed-dispense op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and feed? (nil? c))
      (conj {:rule :unknown-coop :detail "未登録 coop への給餌承認は不可"})

      (and feed? c (not= (:client-id c) (:client-id request)))
      (conj {:rule :coop-wrong-client :detail "coop が別 client のもの"})

      (and feed? c (number? feed-kg) (pos? (:bird-count c))
           (> (/ feed-kg (:bird-count c)) (:max-feed-kg-per-bird c)))
      (conj {:rule :feed-exceeds-per-bird-ceiling
             :detail (str "1羽あたり給餌量 " (double (/ feed-kg (:bird-count c)))
                          "kg > 登録済み上限 " (:max-feed-kg-per-bird c)
                          "kg（過給餌は算術であって動物福祉の判断ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `poultry.store/Store`. Pure — never mutates the
  store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        c (some->> (:coop-id proposal) (store/coop store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record c)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
