(ns hap-todo.app
  (:use plumbing.core)
  (:require [bidi.bidi :as bidi]
            [bidi.ring :as bidi-ring]
            [ring-hap.core :refer [wrap-hap]]
            [hap-todo.middleware.cors :refer [wrap-cors]]
            [hap-todo.route :refer [routes]]
            [hap-todo.handler :refer [handlers]])
  (:import [java.net URI]))

(defn path-for [routes]
  (fn [handler & params]
    (URI/create (apply bidi/path-for routes handler params))))

(defn wrap-path-for [handler path-for]
  (fn [req] (handler (assoc req :path-for path-for))))

(defnk app [:as opts]
  (let [routes (routes)
        path-for (path-for routes)
        opts (assoc opts :path-for path-for)]
    (-> (bidi-ring/make-handler routes (handlers opts))
        (wrap-path-for path-for)
        (wrap-hap {:up-href (path-for :service-document-handler)})
        (wrap-cors))))