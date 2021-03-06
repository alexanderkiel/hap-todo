(ns hap-todo.handler-test
  (:use plumbing.core)
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [hap-todo.handler :refer :all]
            [hap-todo.api :as api]))

(defn- path-for [handler & args] (pr-str {:handler handler :args args}))

(def ^:private id #uuid "746c82be-e6ec-4dd6-b5e4-4dfe1f4037e0")
(def ^:private id-1 #uuid "68f97840-fc7b-4943-9923-436d60477c9a")
(def ^:private id-2 #uuid "348fc80a-fdb9-44ab-8965-b38b85756b37")

(defn- db [& items] (atom (apply api/db items)))

(defn- href [resp rel]
  (edn/read-string (-> resp :body :links rel :href)))

(defn- embedded [resp rel]
  (-> resp :body :embedded rel))

(defn- query [resp rel]
  (-> resp :body :queries rel))

(defn- form [resp rel]
  (-> resp :body :forms rel))

(defn- error-msg [resp]
  (-> resp :body :data :message))

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

(defn- etag [db handler id]
  (-> (execute handler :get :params {:id id} :db db)
      (get-in [:headers "ETag"])))

(deftest item-handler-test
  (let [resp (execute item-handler :get
               :params {:id id}
               :db (db {:id id :label "label-160856"}))]

    (is (= 200 (:status resp)))

    (testing "contains an up link"
      (is (= :service-document-handler (:handler (href resp :up)))))

    (testing "contains a self link"
      (is (= :item-handler (:handler (href resp :self))))
      (is (= [:id id] (:args (href resp :self)))))

    (testing "contains an state link"
      (is (= :item-state-handler (:handler (href resp :todo/item-state))))
      (is (= [:id id] (:args (href resp :todo/item-state)))))

    (testing "contains the delete operation"
      (is (contains? (-> resp :body :ops) :delete)))

    (testing "contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "contains the label"
      (is (= "label-160856" (-> resp :body :data :label)))))

  (testing "Delete succeeds"
    (let [db (db {:id id})
          resp (execute item-handler :delete
                 :params {:id id}
                 :db db)]
      (is (= 204 (:status resp)))
      (is (empty? (:items @db)))
      (is (empty? (:all @db))))))

(deftest item-state-handler-test
  (let [resp (execute item-state-handler :get
               :params {:id id}
               :db (db {:id id :state :active}))]

    (is (= 200 (:status resp)))

    (testing "contains an up link"
      (is (= :item-handler (:handler (href resp :up))))
      (is (= [:id id] (:args (href resp :up)))))

    (testing "contains a self link"
      (is (= :item-state-handler (:handler (href resp :self))))
      (is (= [:id id] (:args (href resp :self)))))

    (testing "contains a profile link"
      (is (= :item-state-profile-handler (:handler (href resp :profile)))))

    (testing "contains the update operation"
      (is (contains? (-> resp :body :ops) :update)))

    (testing "contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "contains the state"
      (is (= :active (-> resp :body :data :state)))))

  (testing "Non-conditional update fails"
    (let [resp (execute item-state-handler :put
                 :params {:id id})]
      (is (= 400 (:status resp)))
      (is (= "Require conditional update." (error-msg resp)))))

  (testing "Update fails on missing state"
    (let [resp (execute item-state-handler :put
                 :params {:id id}
                 :body {:data {}}
                 [:headers "if-match"] "\"foo\"")]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity: {:state missing-required-key}"
             (error-msg resp)))))

  (testing "Update fails on invalid state"
    (let [resp (execute item-state-handler :put
                 :params {:id id}
                 :body {:data {:state "foo"}}
                 [:headers "if-match"] "\"foo\"")]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity: {:state (not (#{:completed :active} \"foo\"))}"
             (error-msg resp)))))

  (testing "Update fails on ETag missmatch"
    (let [resp (execute item-state-handler :put
                 :params {:id id}
                 :body {:data {:state :active}}
                 :db (db {:id id})
                 [:headers "if-match"] "\"foo\"")]
      (is (= 412 (:status resp)))
      (is (= "Precondition Failed" (error-msg resp)))))

  (testing "Update succeeds"
    (let [db (db {:id id})
          resp (execute item-state-handler :put
                 :params {:id id}
                 :body {:data {:state :active}}
                 :db db
                 [:headers "if-match"] (etag db item-state-handler id))]
      (is (= 204 (:status resp)))
      (is (get-in resp [:headers "ETag"]))
      (is (= :active (get-in @db [:items id :state])))))

  (testing "Delete succeeds"
    (let [db (db {:id id})
          resp (execute item-handler :delete
                 :params {:id id}
                 :db db)]
      (is (= 204 (:status resp)))
      (is (empty? (:items @db)))
      (is (empty? (:all @db))))))

(deftest item-state-profile-handler-test
  (let [resp (execute item-state-profile-handler :get
               :params {:id id}
               :db (db {:id id}))]

    (is (= 200 (:status resp)))

    (testing "contains an up link"
      (is (= :service-document-handler (:handler (href resp :up)))))

    (testing "contains a self link"
      (is (= :item-state-profile-handler (:handler (href resp :self)))))

    (testing "contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "contains the schema"
      (is (-> resp :body :data :schema)))))

(deftest item-list-handler-test

(testing "List contains filter query with param and label"
    (let [resp (execute item-list-handler :get
                        :db (db))]
      (is (query resp :todo/filter-item))
      (is (= (:label (query resp :todo/filter-item)) "Search Items by Label"))
      (is (:label (:params (query resp :todo/filter-item))))
      (is (= (:label (:label (:params (query resp :todo/filter-item)))))
          "A string which is contained in labels of ToDo items to find.")))

(testing "List contains create form with param and label"
    (let [resp (execute item-list-handler :get
                        :db (db))]
      (is (form resp :todo/create-item))
      (is (= (:label (form resp :todo/create-item)) "Create Item"))
      (is (:label (:params (form resp :todo/create-item))))
      (is (= (:label (:label (:params (form resp :todo/create-item))))
             "The label of the ToDo item (what should be done)."))))

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
      (is (= "label-021742" (-> (first (embedded resp :todo/items)) :data :label)))))

  (testing "List on DB with two items orders them by insertion order"
    (let [resp (execute item-list-handler :get
                 :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (embedded resp :todo/items))))
      (is (= "a" (-> (first (embedded resp :todo/items)) :data :label)))
      (is (= "b" (-> (second (embedded resp :todo/items)) :data :label)))))

  (testing "List on DB with labels [a b] returns none when filtered by 'z'"
    (let [resp (execute item-list-handler :get
                        :params {:label "z"}
                        :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 0 (count (embedded resp :todo/items))))))

  (testing "List on DB with labels [a b] returns one when filtered by 'a'"
    (let [resp (execute item-list-handler :get
                        :params {:label "a"}
                        :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 1 (count (embedded resp :todo/items))))))

  (testing "List on DB with labels [ab bc] returns all when filtered by 'b'"
    (let [resp (execute item-list-handler :get
                        :params {:label "b"}
                        :db (db {:id id-1 :label "ab"} {:id id-2 :label "bc"}))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (embedded resp :todo/items))))))

  (testing "List on DB with labels [a b] returns all when filter is empty"
    (let [resp (execute item-list-handler :get
                        :params {:label ""}
                        :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (embedded resp :todo/items))))))

  (testing "List on DB with labels [a b] returns all when filter is nil"
    (let [resp (execute item-list-handler :get
                        :params {:label nil}
                        :db (db {:id id-1 :label "a"} {:id id-2 :label "b"}))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (embedded resp :todo/items))))))

  (testing "Create without label fails"
    (let [resp (execute item-list-handler :post)]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity: {:label missing-required-key}" (error-msg resp)))))

  (testing "Create with invalid label fails"
    (let [resp (execute item-list-handler :post
                 :params {:label :foo})]
      (is (= 422 (:status resp)))
      (is (= "Unprocessable Entity: {:label (not (instance? java.lang.String :foo))}"
             (error-msg resp)))))

  (testing "Create succeeds"
    (let [db (db)
          resp (execute item-list-handler :post
                 :params {:label "label-152935"}
                 :db db)]
      (is (= 201 (:status resp)))
      (is (second (:args (location resp))))
      (is (nil? (:body resp)))

      (testing ":items is a map containing one item"
        (is (map? (:items @db)))
        (is (= 1 (count (:items @db)))))

      (testing "Created item is active"
        (is (= :active (:state (first (vals (:items @db))))))))))
