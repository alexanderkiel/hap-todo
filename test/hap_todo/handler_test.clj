(ns hap-todo.handler-test
  (:use plumbing.core)
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [hap-todo.handler :refer :all])
  (:import [java.util UUID]))

(defn- uuid [s] (UUID/fromString s))

(defn- path-for [handler & args] (pr-str {:handler handler :args args}))

(def ^:private id (uuid "746c82be-e6ec-4dd6-b5e4-4dfe1f4037e0"))

(defn- db [& items]
  (atom (for-map [item items] (:id item) item)))

(defn- href [resp]
  (edn/read-string (-> resp :body :links :self :href)))

(defn- location [resp]
  (edn/read-string (get-in resp [:headers "Location"])))

(defn- request [method & kvs]
  (reduce-kv
    (fn [m k v]
      (if (sequential? k)
        (assoc-in m k v)
        (assoc m k v)))
    {:request-method method
     :headers {"accept" "*/*"}
     :path-for path-for
     :params {}}
    (apply hash-map kvs)))

(defn execute [handler method & kvs]
  (handler (apply request method kvs)))

(defn- etag [db id]
  (-> (execute item-handler :get :params {:id id} :db db)
      (get-in [:headers "ETag"])))

(deftest item-handler-test
  (let [resp (execute item-handler :get
               :params {:id id}
               :db (db {:id id}))]

    (is (= 200 (:status resp)))

    (testing "self link"
      (is (= :item-handler (:handler (href resp))))
      (is (= [:id id] (:args (href resp)))))

    (testing "ETag"
      (is (get-in resp [:headers "ETag"]))))

  (testing "Non-conditional update fails"
    (let [resp (execute item-handler :put
                 :params {:id id})]
      (is (= 400 (:status resp)))
      (is (= "Require conditional update." (:error (:body resp))))))

  (testing "Update fails on missing label"
    (let [resp (execute item-handler :put
                 :params {:id id}
                 [:headers "if-match"] "\"foo\"")]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity" (:error (:body resp))))))

  (testing "Update fails on ETag missmatch"
    (let [resp (execute item-handler :put
                 :params {:id id :label "foo"}
                 :db (db {:id id})
                 [:headers "if-match"] "\"foo\"")]
      (is (= 412 (:status resp)))
      (is (= "Precondition Failed" (:error (:body resp))))))

  (testing "Update succeeds"
    (let [db (db {:id id})
          resp (execute item-handler :put
                 :params {:id id :label "foo"}
                 :db db
                 [:headers "if-match"] (etag db id))]
      (is (= 204 (:status resp))))))

(deftest item-list-handler-test
  (testing "Create without label fails"
    (let [resp (execute item-list-handler :post)]
      (is (= 422 (:status resp)))))

  (testing "Create succeeds"
    (let [resp (execute item-list-handler :post
                 :params {:label "label-152935"}
                 :db (db))]
      (is (= 201 (:status resp)))
      (is (second (:args (location resp))))
      (is (nil? (:body resp))))))
