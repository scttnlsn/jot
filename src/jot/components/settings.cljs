(ns jot.components.settings
  (:require-macros [kioo.om :as kioo])
  (:require [cljs.core.async :refer [>! chan put!]]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om.core :as om]
            [kioo.om :refer [content do-> set-attr]]
            [jot.note :as note]))

(defcomponent settings [cursor owner]
  (render [_]
    (let [action-ch (om/get-shared owner :action-ch)]
      (kioo/component "templates/settings.html" [:#settings]
        {[:.status] (content
                     (str "Dropbox syncing is " (if (:connected cursor) "on" "off") "."))
         [:button] (do->
                    (set-attr :onClick #(put! action-ch [:toggle-sync {}]))
                    (content
                     (if (:connected cursor) "Disconnect" "Connect")))}))))
