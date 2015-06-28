(ns hap-todo.handler
  (:use plumbing.core)
  (:require [liberator.core :refer [resource to-location]]
            [pandect.algo.md5 :refer [md5]]))

;; ---- Util ------------------------------------------------------------------

(def resource-defaults
  {:available-media-types ["application/transit+json"]

   :as-response (fn [data _] {:body data})

   :handle-not-modified nil})

;; ---- Service Document ------------------------------------------------------

(defn render-service-document [version]
  (fnk [[:request path-for]]
       {:name "HAP ToDo"
        :version version
        :links
        {:self {:href (path-for :service-document-handler)}}}))

(defn service-document-handler [version]
  (resource
    resource-defaults

    :etag
    (fnk [representation [:request path-for]]
         (md5 (str (:media-type representation)
                   (path-for :service-document-handler))))

    :handle-ok (render-service-document version)))

;; ---- Handlers --------------------------------------------------------------

(defnk handlers [path-for version]
  {:service-document-handler (service-document-handler version)})
