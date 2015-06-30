(ns hap-todo.system
  (:use plumbing.core)
  (:require [org.httpkit.server :refer [run-server]]
            [hap-todo.app :refer [app]]
            [hap-todo.util :refer [parse-long]]))

(defn create [env]
  (-> (assoc env :app app)
      (assoc :db (atom {}))
      (assoc :version (:hap-todo-version env))
      (update :ip (fnil identity "0.0.0.0"))
      (update :port (fnil parse-long "8080"))
      (update :thread (fnil parse-long "4"))))

(defnk start [app port & more :as system]
  (let [stop-fn (run-server (app more) {:port port})]
    (assoc system :stop-fn stop-fn)))

(defn stop [{:keys [stop-fn] :as system}]
  (stop-fn)
  (dissoc system :stop-fn))