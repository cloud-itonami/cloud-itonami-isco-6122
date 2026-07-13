(ns poultry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [poultry.actor :as actor]
            [poultry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Farms"})
    (store/register-coop! st {:coop-id "C-1" :client-id "client-1"
                              :name "coop-north"
                              :bird-count 100
                              :max-feed-kg-per-bird 0.15})
    st))

(deftest commits-an-in-ceiling-feed-dispense
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-feed-dispense :stake :low
                 :coop-id "C-1" :feed-kg 10}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-ceiling-feed-dispense
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-feed-dispense :stake :low
                 :coop-id "C-1" :feed-kg 50}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-administers-medication-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :administer-medication :stake :low
                 :coop-id "C-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
