(ns hap-todo.handler
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [schema.core :as s]
            [liberator.core :as l :refer [resource to-location]]
            [liberator.representation :refer [Representation as-response]]
            [digest.core :as digest]
            [hap-todo.api :as api])
  (:import [java.util UUID])
  (:refer-clojure :exclude [error-handler]))

;; ---- Util ------------------------------------------------------------------

(extend-protocol Representation
  clojure.lang.MapEquivalence
  (as-response [this _] {:body this}))

(def resource-defaults
  {:available-media-types ["application/transit+json"
                           "application/transit+msgpack"]

   :as-response as-response

   :handle-not-modified nil})

(defnk entity-malformed
  "Standard malformed decision for single entity resources.

  Parsed entity will be placed under :new-entity in the context in case of
  success. Otherwise :error will be placed."
  [request :as ctx]
  (if (or (not (l/=method :put ctx)) (l/header-exists? "if-match" ctx))
    (when (l/=method :put ctx)
      (if-let [body (:body request)]
        [false {:new-entity body}]
        {:error "Missing request body."}))
    {:error "Require conditional update."}))

(defn validate [schema x]
  (if-let [error (s/check schema x)]
    [false {:error (str "Unprocessable Entity: " (pr-str error))}]
    true))

(defn- entity-processable [schema]
  (fn [ctx]
    (or (not (l/=method :put ctx))
        (validate schema (:data (:new-entity ctx))))))

(defn- form-params-valid [schema]
  (fnk [[:request params request-method]]
    (or (not= :post request-method)
        (validate schema params))))

(defn- error-body [path-for msg]
  {:data {:message msg}
   :links {:up {:href (path-for :service-document-handler)}}})

(defrecord StatusResponse [status response]
  Representation
  (as-response [_ context]
    (assoc (as-response response context) :status status)))

(defn- error [path-for status msg]
  (->StatusResponse status (error-body path-for msg)))

(defn- error-handler [msg]
  (fnk [[:request path-for] :as ctx]
    (error-body path-for (or (:error ctx) msg))))

(def entity-resource-defaults
  (assoc
    resource-defaults

    :allowed-methods [:get :put :delete]

    :malformed? entity-malformed

    :can-put-to-missing? false

    :new? false

    :handle-no-content
    (fnk [[:request path-for] :as ctx]
      (condp = (:update-error ctx)
        :not-found (error path-for 404 "Not Found")
        :conflict (error path-for 409 "Conflict")
        nil))

    :handle-malformed (error-handler "Malformed")
    :handle-unprocessable-entity (error-handler "Unprocessable Entity")
    :handle-precondition-failed (error-handler "Precondition Failed")

    :handle-not-found
    (fnk [[:request path-for]] (error-body path-for "Not Found"))))

(def list-resource-defaults
  (assoc
    resource-defaults

    :allowed-methods [:get :post]

    :can-post-to-missing? false

    :handle-unprocessable-entity (error-handler "Unprocessable Entity")))

;; ---- Service Document ------------------------------------------------------

(defn render-create-item-form [path-for]
  {:href (path-for :item-list-handler)
   :label "Create Item"
   :params
   {:label {:type s/Str
            :label "The label of the ToDo item (what should be done)."}}})

(defn render-filter-items-query [path-for]
  {:href (path-for :item-list-handler)
   :label "Search Items by Label"
   :params
   {:label {:type s/Str
            :label "A string which is contained in labels of ToDo items to find."}}})

(defn render-service-document [version]
  (fnk [[:request path-for]]
    {:data
     {:name "HAP ToDo"
      :version version}
     :links
     {:self {:href (path-for :service-document-handler)}
      :todo/items {:href (path-for :item-list-handler)}}
     :forms
     {:todo/create-item
      (render-create-item-form path-for)}
     :queries
     {:todo/filter-item (render-filter-items-query path-for)}}))

(defn service-document-handler [version]
  (resource
    resource-defaults

    :etag
    (fnk [representation]
      (digest/md5 (:media-type representation)))

    :handle-ok (render-service-document version)))

;; ---- Items -----------------------------------------------------------------

(defn item-path [path-for item]
  (path-for :item-handler :id (:id item)))

(defn item-state-path [path-for item]
  (path-for :item-state-handler :id (:id item)))

(defn render-embedded-item [path-for item]
  {:pre [(map? item)]}
  {:data (dissoc item :id :rank)
   :links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (item-path path-for item)}
    :todo/item-state {:href (item-state-path path-for item)}}
   :ops #{:delete}})

(defn render-embedded-item-xf
  "Returns a transducer which maps over a coll containing maps with :id."
  [path-for db label]
  (comp
    (map :id)
    (map (:items @db))
    (filter (if (str/blank? label) identity #(.contains (:label %) label)))
    (map #(render-embedded-item path-for %))))

(defnk render-item-list [[:request [:params {label nil}] db path-for]]
  {:links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (path-for :item-list-handler)}}
   :forms
   {:todo/create-item
    (render-create-item-form path-for)}
   :queries
   {:todo/filter-item (render-filter-items-query path-for)}
   :embedded
   {:todo/items
    (into [] (render-embedded-item-xf path-for db label) (:all @db))}})

(def item-list-handler
  (resource
    list-resource-defaults

    :processable? (form-params-valid {:label s/Str})

    :post!
    (fnk [[:request db [:params label]]]
      (let [id (UUID/randomUUID) item {:id id :label label :state :active}]
        (swap! db api/add-item item)
        {:item item}))

    :location (fnk [item [:request path-for]] (item-path path-for item))

    :handle-ok render-item-list))

(defnk render-item [item [:request path-for]]
  (render-embedded-item path-for item))

(def item-handler
  (resource
    entity-resource-defaults

    :allowed-methods [:get :delete]

    :exists?
    (fnk [[:request db [:params id]]]
      (when-let [item (get-in @db [:items id])]
        {:item item}))

    :etag
    (fnk [representation :as ctx]
      (when-let [item (:item ctx)]
        (digest/md5 (:media-type representation) (:label item))))

    :delete!
    (fnk [[:request db] item]
      (swap! db api/delete-item item))

    :handle-ok render-item

    :handle-not-found
    (fnk [[:request path-for]] (error-body path-for "Item not found."))))

(defnk render-item-state [item [:request path-for]]
  {:data (select-keys item [:state])
   :links
   {:up {:href (item-path path-for item)}
    :self {:href (item-state-path path-for item)}
    :profile {:href (path-for :item-state-profile-handler)}}
   :ops #{:update}})

(def ^:private item-state-schema {:state (s/enum :active :completed)})

(def item-state-handler
  (resource
    entity-resource-defaults

    :allowed-methods [:get :put]

    :exists?
    (fnk [[:request db [:params id]]]
      (when-let [item (get-in @db [:items id])]
        {:item item}))

    :processable? (entity-processable item-state-schema)

    :etag
    (fnk [representation :as ctx]
      (when-let [item (:item ctx)]
        (digest/md5 (:media-type representation) (:state item))))

    :put!
    (fnk [[:request db] item new-entity]
      ;;TODO check for item equality inside swap
      (let [db (swap! db api/update-item-state item (:state (:data new-entity)))]
        {:item (get-in db [:items (:id item)])}))

    :handle-ok render-item-state

    :handle-not-found
    (fnk [[:request path-for]] (error-body path-for "Item not found."))))

(defnk render-item-state-profile [profile [:request path-for]]
  {:data profile
   :links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (path-for :item-state-profile-handler)}}})

(def item-state-profile-handler
  (resource
    resource-defaults

    :exists?
    {:profile {:schema item-state-schema}}

    :etag
    (fnk [representation :as ctx]
      (when-let [profile (:profile ctx)]
        (digest/md5 (:media-type representation) profile)))

    :handle-ok render-item-state-profile

    :handle-not-found
    (fnk [[:request path-for]] (error-body path-for "Item state profile not found."))))

;; ---- Handlers --------------------------------------------------------------

(defnk handlers [version]
  {:service-document-handler (service-document-handler version)
   :item-list-handler item-list-handler
   :item-handler item-handler
   :item-state-handler item-state-handler
   :item-state-profile-handler item-state-profile-handler})
