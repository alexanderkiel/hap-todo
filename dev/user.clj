(ns user
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]
            [hap-todo.system :as s]))

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (s/create env))))

(defn start []
  (alter-var-root #'system s/start))

(defn stop []
  (alter-var-root #'system s/stop))

(defn startup []
  (init)
  (start)
  (println "Server running at port" (:port system)))

(defn reset []
  (stop)
  (refresh :after 'user/startup))

;; Init Development
(comment
  (startup)
  )

;; Reset after making changes
(comment
  (reset)
  )
