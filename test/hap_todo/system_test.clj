(ns hap-todo.system-test
  (:require [clojure.test :refer :all]
            [hap-todo.system :refer :all]))

(deftest system-test
  (testing "Default ip is 0.0.0.0"
    (is (= "0.0.0.0" (:ip (create {})))))
  (testing "Default port is 8080"
    (is (= 8080 (:port (create {})))))
  (testing "Non default port is taken"
    (is (= 5000 (:port (create {:port "5000"})))))
  (testing "Default number of threads is 4"
    (is (= 4 (:thread (create {}))))))
