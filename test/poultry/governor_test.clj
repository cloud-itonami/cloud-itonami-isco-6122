(ns poultry.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [poultry.store :as store]
            [poultry.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Farms"})
    (store/register-coop! st {:coop-id "C-1" :client-id "client-1"
                              :name "coop-north"
                              :bird-count 100
                              :max-feed-kg-per-bird 0.15})
    st))

(defn- feed [kg]
  {:op :approve-feed-dispense :effect :propose :coop-id "C-1"
   :feed-kg kg :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-per-bird-ceiling
  (let [st (fresh-store)
        v (governor/check req {} (feed 10) st)]
    (is (:ok? v))))

(deftest ok-at-exact-per-bird-ceiling
  (testing "feed exactly at the per-bird ceiling is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (feed 15) st)]
      (is (:ok? v)))))

(deftest hard-on-feed-exceeds-per-bird-ceiling
  (testing "overfeeding is arithmetic, not animal welfare judgement"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (feed 30) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :feed-exceeds-per-bird-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-unknown-coop
  (let [st (fresh-store)
        v (governor/check req {} (assoc (feed 10) :coop-id "C-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-coop (:rule %)) (:violations v)))))

(deftest hard-on-foreign-coop
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (feed 10) st)]
      (is (:hard? v))
      (is (some #(= :coop-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (feed 10) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (feed 10) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-medication-even-at-high-confidence
  (testing "no veterinary medication administration without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :administer-medication :effect :propose
                                    :coop-id "C-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-outbreak-containment-even-at-high-confidence
  (testing "disease-outbreak containment decisions always require human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-outbreak-containment :effect :propose
                                    :coop-id "C-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (feed 10) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
