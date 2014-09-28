(ns jot.components.notes
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [kioo.om :as kioo]
                   [jot.macros :refer [dochan]])
  (:require [cljs.core.async :refer [>! chan put!]]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om.core :as om]
            [kioo.om :refer [content do-> set-attr substitute]]
            [jot.components.search :as search]
            [jot.note :as note]
            [jot.util :as util]))

(defcomponent tag [cursor owner]
  (render [_]
    (let [action-ch (om/get-shared owner :action-ch)]
      (kioo/component "templates/list.html" [:.tag]
        {[:.tag] (do->
                  (content cursor)
                  (set-attr :onClick #(do
                                        (put! action-ch [:select-tag {:tag cursor}])
                                        false)))}))))

(defcomponent note-item [cursor owner]
  (render-state [this {:keys [select-note select-tag]}]
    (let [action-ch (om/get-shared owner :action-ch)]
      (kioo/component "templates/list.html" [:ul.list :> :li]
                      {[:li] (set-attr :onClick #(put! action-ch [:select-note {:note @cursor}]))
                       [:.title] (content (note/title cursor))
                       [:.subtext] (content (note/summary cursor))
                       [:.tags] (content (om/build-all tag (note/tags cursor)))
                       [:.timestamp] (content (note/date-str cursor))}))))

(defcomponent note-list [cursor owner]
  (init-state [_]
    {:scroll-ch (chan)})

  (will-mount [_]
    (let [action-ch (om/get-shared owner :action-ch)
          scroll-ch (om/get-state owner :scroll-ch)]
      (go
       (dochan [offset (util/debounce scroll-ch 100)]
         (>! action-ch [:scroll {:offset offset}])))))

  (did-mount [_]
    (let [el (om/get-node owner "scrollable")]
      (set! (.-scrollTop el) (:scroll cursor))))

  (render-state [_ {:keys [scroll-ch]}]
    (let [action-ch (om/get-shared owner :action-ch)
          term (:search cursor)
          notes (note/matching (note/sorted (vals (:notes cursor))) term)]
      (kioo/component "templates/list.html" [:#list]
        {[:.right :.btn] (set-attr :onClick #(put! action-ch [:create-note {}]))
         [:.search] (substitute (om/build search/search cursor))
         [:.wrapper] (set-attr :ref "scrollable"
                               :onScroll #(put! scroll-ch (.. % -target -scrollTop)))
         [:ul.list] (content (om/build-all note-item notes))}))))

(defcomponent note-editor [cursor owner]
  (render [_]
    (let [action-ch (om/get-shared owner :action-ch)]
      (kioo/component "templates/editor.html" [:#editor]
        {[:.right :.btn] (set-attr :onClick #(if (js/confirm "Are you sure?")
                                               (put! action-ch [:delete-note {:note @cursor}])))
         [:h1] (content (note/title cursor))
         [:.content] (set-attr :value (:text cursor)
                               :onChange #(put! action-ch [:update-note {:note @cursor
                                                                         :text (.. % -target -value)}]))}))))
