(ns hap-todo.route
  (:require [bidi.bidi :as bidi]))

(defn routes []
  ["/"
   {"" :service-document-handler
    ["items/" [bidi/uuid :id]] :item-handler
    "items" :item-list-handler}])
