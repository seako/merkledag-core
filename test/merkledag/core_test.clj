(ns merkledag.core-test
  (:require
    [blocks.core :as block]
    [blocks.store.memory :refer [memory-store]]
    [byte-streams :as bytes :refer [bytes=]]
    [clj-time.core :as time]
    [clojure.test :refer :all]
    (merkledag
      [data :as data]
      [core :as merkle]
      [test-utils :refer [dprint]])
    [multihash.core :as multihash])
  (:import
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(def hash-1 (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh"))
(def hash-2 (multihash/decode "Qmd8kgzaFLGYtTS1zfF37qKGgYQd5yKcQMyBeSa8UkUz4W"))


#_
(deftest a-test
  (let [repo (graph/block-graph)]
    (testing "basic node properties"
      (let [node (merkle/node (:format repo)
                   [(merkle/link "@context" hash-1)]
                   {:type :finance/posting
                    :uuid "foo-bar"})]
        (is (instance? Multihash (:id node)))
        (is (vector? (:links node)))
        (is (= 1 (count (:links node))))
        (is (every? (partial instance? MerkleLink) (:links node)))
        (is (map? (:data node)))
        (is (empty? (block/list repo))
            "node creation should not store any blocks")))
    (graph/with-context repo
      (testing "multi-node reference"
        (let [node-1 (merkle/node
                       {:type :finance/posting
                        :uuid "foo-bar"})
              node-2 (merkle/node
                       {:type :finance/posting
                        :uuid "frobblenitz omnibus"})
              node-3 (merkle/node
                       [(merkle/link "@context" hash-1)]
                       {:type :finance/transaction
                        :uuid #uuid "31f7dd72-c7f7-4a15-a98b-0f9248d3aaa6"
                        :title "SCHZ - Reinvest Dividend"
                        :description "Automatic dividend reinvestment."
                        :time (data/parse-inst "2013-10-08T00:00:00")
                        :entries [(merkle/link "posting-1" node-1)
                                  (merkle/link "posting-2" node-2)]})]
          ;(bytes/print-bytes (block/open node-3))
          (is (= 3 (count (:links node-3))))
          (is (every? (partial instance? MerkleLink) (:links node-3)))
          (merkle/put-node! repo node-1)
          (merkle/put-node! repo node-2)
          (merkle/put-node! repo node-3)
          (let [node' (merkle/get-node repo (:id node-3))]
            (is (= (:id node') (:id node-3)))
            (is (bytes= (block/open node') (block/open node-3)))
            (is (= (:links node') (:links node-3)))
            (is (= (:data node') (:data node-3)))))))))


; TODO: test raw :data segments