(ns hap-todo.api-test
  (:require [clojure.test :refer :all]
            [hap-todo.api :refer :all]))

(deftest add-item-test
  (testing "after adding an item"
    (let [id "094806" db (add-item (db) {:id id})]
      (testing ":next-rank is 2"
        (is (= 2 (:next-rank db))))
      (testing "item has rank 1"
        (is (= 1 (:rank ((:items db) id)))))
      (testing ":all remains sorted"
        (is (sorted? (:all db)))))))

(deftest delete-item-test
  (testing "after deleting an item"
    (let [db (delete-item (db {:id "095225"}) {:id "095225" :rank 1})]
      (testing ":all remains sorted"
        (is (sorted? (:all db)))))))
