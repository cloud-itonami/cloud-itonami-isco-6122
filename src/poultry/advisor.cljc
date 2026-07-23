(ns poultry.advisor
  "PoultryAdvisor — the advisor named in this repository's README,
  proposing a coop operation (approve a feed dispense, administer
  medication, approve outbreak containment) from a flock management
  plan and feed schedule. Swappable mock/llm; the advisor ONLY
  proposes — `poultry.governor` checks the per-bird feed ceiling
  independently and always escalates medication/outbreak decisions.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-feed-dispense|:administer-medication|:approve-outbreak-containment
               :effect :propose :coop-id str :feed-kg number
               :stake kw :confidence n :rationale str}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake coop-id feed-kg] :as request}]
  {:op op
   :effect :propose
   :coop-id coop-id
   :feed-kg feed-kg
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a poultry-operations advisor. Given a request, propose an
   :op, the :coop-id and :feed-kg, an honest :confidence and a :stake.
   Never call an over-ceiling feed dose conforming — the governor
   divides the proposed feed by the registered bird count and checks
   the per-bird result. Medication and outbreak-containment decisions
   always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
