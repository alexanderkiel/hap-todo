(ns hap-todo.api
  (:use plumbing.core))

(defn- new-db []
  {:items {}
   :all (sorted-set-by (comparator #(< (:rank %1) (:rank %2))))
   :next-rank 1})

(defn add-item
  "Adds item to db returning a new db."
  [db {:keys [id] :as item}]
  (let [rank (:next-rank db)]
    (-> (assoc-in db [:items id] (assoc item :rank rank))
        (update :next-rank inc)
        (update :all #(conj % {:id id :rank rank})))))

(defn db
  "Returns a db value containing all items."
  [& items]
  (reduce add-item (new-db) items))

(defn delete-item
  "Deletes item in db returning a new db."
  [db item]
  (-> (dissoc-in db [:items (:id item)])
      (update :all #(disj % (select-keys item [:id :rank])))))

(defn- concurrent-update-ex-info [{:keys [id] :as item}]
  (ex-info (str "Concurrent update on item " id " detected.") {:item item}))

(defn update-item-state [db {id :id old-state :state :as item} new-state]
  (->> #(if (= old-state %) new-state (throw (concurrent-update-ex-info item)))
       (update-in db [:items id :state])))
