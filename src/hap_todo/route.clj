(ns hap-todo.route
  (:require [bidi.bidi :as bidi]))

(defn routes []
  ["/"
   {"" :service-document-handler
    ["items/" [bidi/uuid :id]] :item-handler
    ["items/" [bidi/uuid :id] "/state"] :item-state-handler
    "items" :item-list-handler
    "item-state-profile" :item-state-profile-handler}])
