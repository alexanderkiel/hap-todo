(ns hap-todo.route-test
  (:require [clojure.test :refer :all]
            [bidi.bidi :as bidi]
            [hap-todo.route :refer :all])
  (:import [java.util UUID]))

(def id (UUID/fromString "76450296-d005-417f-9b3a-ce80fe4bdfb9"))

(deftest routes-test
  (testing "Item"
    (is (= (str "/items/" id) (bidi/path-for (routes) :item-handler :id id))))
  (testing "Item string id invalid"
    (is (thrown? Exception (bidi/path-for (routes) :item-handler :id "a")))))
