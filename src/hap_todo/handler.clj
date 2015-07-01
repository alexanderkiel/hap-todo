(ns hap-todo.handler
  (:use plumbing.core)
  (:require [liberator.core :as l :refer [resource to-location]]
            [liberator.representation :refer [Representation as-response]]
            [pandect.algo.md5 :refer [md5]])
  (:import [java.util UUID])
  (:refer-clojure :exclude [error-handler]))

;; ---- Util ------------------------------------------------------------------

(extend-protocol Representation
  clojure.lang.MapEquivalence
  (as-response [this _] {:body this}))

(def resource-defaults
  {:available-media-types ["application/transit+json"]

   :as-response as-response

   :handle-not-modified nil})

(defnk entity-malformed
  "Standard malformed decision for single entity resources.

  Parsed entity will be placed under :new-entity in the context in case of
  success. Otherwise :error will be placed."
  [request :as ctx]
  (if (or (not (l/=method :put ctx)) (l/header-exists? "if-match" ctx))
    (when (l/=method :put ctx)
      (if-let [params (:params request)]
        [false {:new-entity params}]
        {:error "Missing request body."}))
    {:error "Require conditional update."}))

(defn- entity-processable [& params]
  (fn [ctx]
    (or (not (l/=method :put ctx))
        (every? identity (map #(get-in ctx [:new-entity %]) params)))))

(defn- error-body [path-for msg]
  {:links {:up {:href (path-for :service-document-handler)}}
   :error msg})

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
   :title "Create Item"
   :params
   {:label {:type 'Str
            :desc "The label of the ToDo item (what should be done)."}}})

(defn render-service-document [version]
  (fnk [[:request path-for]]
    {:name "HAP ToDo"
     :version version
     :links
     {:self {:href (path-for :service-document-handler)}
      :todo/items {:href (path-for :item-list-handler)}}
     :forms
     {:todo/create-item
      (render-create-item-form path-for)}}))

(defn service-document-handler [version]
  (resource
    resource-defaults

    :etag
    (fnk [representation [:request path-for]]
      (md5 (str (:media-type representation)
                (path-for :service-document-handler)
                (path-for :item-list-handler))))

    :handle-ok (render-service-document version)))

;; ---- Handlers --------------------------------------------------------------

(defn item-path [path-for item]
  (path-for :item-handler :id (:id item)))

(defn render-embedded-item [path-for item]
  {:pre [(map? item)]}
  (-> (dissoc item :id)
      (assoc
        :links
        {:up {:href (path-for :service-document-handler)}
         :self {:href (item-path path-for item)}})))

(defnk render-item-list [[:request db path-for]]
  {:links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (path-for :item-list-handler)}}
   :forms
   {:todo/create-item
    (render-create-item-form path-for)}
   :embedded
   {:todo/items
    (into [] (comp
                   (map (:items @db))
                   (map #(render-embedded-item path-for %))) (:insert-order @db))}})

(def item-list-handler
  (resource
    list-resource-defaults

    :processable?
    (fnk [[:request request-method params]]
      (or (= :get request-method)
          (:label params)
          [false {:error (str "Param :label missing in "
                              (or (keys params) "empty")
                              " params.")}]))

    :post!
    (fnk [[:request db [:params label]]]
      (let [id (UUID/randomUUID) item {:id id :label label}]
        (swap! db (fn [db] (-> (assoc-in db [:items id] item)
                               (update :insert-order #(conj % id)))))
        {:item item}))

    :location (fnk [item [:request path-for]] (item-path path-for item))

    :handle-ok render-item-list))

(defnk render-item [item [:request path-for]]
  (render-embedded-item path-for item))

(def item-handler
  (resource
    entity-resource-defaults

    :exists?
    (fnk [[:request db [:params id]]]
      (when-let [item (get-in @db [:items id])]
        {:item item}))

    :processable? (entity-processable :label)

    ;;TODO: simplyfy when https://github.com/clojure-liberator/liberator/issues/219 is closed
    :etag
    (fnk [representation {status 200} :as ctx]
      (when (= 200 status)
        (letk [[item] ctx]
          (md5 (str (:media-type representation)
                    (:label item))))))
    :put!
    (fnk [[:request db] item new-entity]
      ;;TODO check for item equality inside swap
      (swap! db #(assoc-in % [:items (:id item)] new-entity)))

    :delete!
    (fnk [[:request db] [:item id]]
      (swap! db #(dissoc % id)))

    :handle-ok render-item

    :handle-not-found
    (fnk [[:request path-for]] (error-body path-for "Item not found."))))

;; ---- Handlers --------------------------------------------------------------

(defnk handlers [version]
  {:service-document-handler (service-document-handler version)
   :item-list-handler item-list-handler
   :item-handler item-handler})
