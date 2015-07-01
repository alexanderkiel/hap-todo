(ns hap-todo.handler-test
  (:use plumbing.core)
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [hap-todo.handler :refer :all]))

(defn- path-for [handler & args] (pr-str {:handler handler :args args}))

(def ^:private id #uuid "746c82be-e6ec-4dd6-b5e4-4dfe1f4037e0")
(def ^:private id-1 #uuid "68f97840-fc7b-4943-9923-436d60477c9a")
(def ^:private id-2 #uuid "348fc80a-fdb9-44ab-8965-b38b85756b37")

(defn- db [& items]
  (atom {:items (for-map [item items] (:id item) item)
         :insert-order (mapv :id items)}))

(defn- href [resp]
  (edn/read-string (-> resp :body :links :self :href)))

(defn- embedded [resp rel]
  (-> resp :body :embedded rel))

(defn- error [resp]
  (-> resp :body :error))

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
      (is (= "Require conditional update." (error resp)))))

  (testing "Update fails on missing label"
    (let [resp (execute item-handler :put
                 :params {:id id}
                 [:headers "if-match"] "\"foo\"")]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity" (error resp)))))

  (testing "Update fails on ETag missmatch"
    (let [resp (execute item-handler :put
                 :params {:id id :label "foo"}
                 :db (db {:id id})
                 [:headers "if-match"] "\"foo\"")]
      (is (= 412 (:status resp)))
      (is (= "Precondition Failed" (error resp)))))

  (testing "Update succeeds"
    (let [db (db {:id id})
          resp (execute item-handler :put
                 :params {:id id :label "foo"}
                 :db db
                 [:headers "if-match"] (etag db id))]
      (is (= 204 (:status resp))))))

(deftest item-list-handler-test

  (testing "List on empty DB return an empty list"
    (let [resp (execute item-list-handler :get
                 :db (db))]
      (is (= 200 (:status resp)))
      (is (empty? (embedded resp :todo/items)))))

  (testing "List on DB with one item returns a list with this item"
    (let [resp (execute item-list-handler :get
                 :db (db {:id id :label "label-021742"}))]
      (is (= 200 (:status resp)))
      (is (= 1 (count (embedded resp :todo/items))))
      (is (= "label-021742" (:label (first (embedded resp :todo/items)))))))

  (testing "List on DB with two items orders them by insertion order"
    (let [resp (execute item-list-handler :get
                 :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (embedded resp :todo/items))))
      (is (= "a" (:label (first (embedded resp :todo/items)))))
      (is (= "b" (:label (second (embedded resp :todo/items)))))))

  (testing "Create without label fails"
    (let [resp (execute item-list-handler :post)]
      (is (= 422 (:status resp)))
      (is (= "Param :label missing in empty params." (error resp)))))

  (testing "Create succeeds"
    (let [db (db)
          resp (execute item-list-handler :post
                 :params {:label "label-152935"}
                 :db db)]
      (is (= 201 (:status resp)))
      (is (second (:args (location resp))))
      (is (nil? (:body resp)))

      (testing ":insert-order is a vector containing one id"
        (is (vector? (:insert-order @db)))
        (is (= 1 (count (:insert-order @db)))))

      (testing ":items is a map containing one item"
        (is (map? (:items @db)))
        (is (= 1 (count (:items @db))))))))
