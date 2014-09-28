(ns jot.components.search
  (:require-macros [kioo.om :as kioo])
  (:require [cljs.core.async :refer [>! chan put!]]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om.core :as om]
            [kioo.om :refer [content do-> set-attr]]
            [jot.note :as note]))

(defcomponent search [cursor owner]
  (render [_]
    (let [action-ch (om/get-shared owner :action-ch)]
      (kioo/component "templates/list.html" [:.search]
        {[:input] (set-attr :value (:search cursor)
                            :onChange #(put! action-ch [:search {:term (.. % -target -value)}]))
         [:.remove] (set-attr :onClick #(put! action-ch [:search {:term ""}]))}))))
