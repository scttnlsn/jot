(ns jot.ui
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [kioo.om :as kioo]
                   [jot.macros :refer [dochan]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan]]
            [kioo.om :refer [content do-> set-attr substitute]]
            [clojure.string :as string]
            [jot.note :as jn]
            [jot.util :as util]))

(defn search [_ owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [term searches]}]
      (kioo/component "templates/list.html" [:.search]
        {[:input] (set-attr :value term
                            :onChange #(put! searches (.. % -target -value)))
         [:.remove] (set-attr :onClick #(put! searches ""))}))))

(defn tag [tag owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [select-tag]}]
      (kioo/component "templates/list.html" [:.tag]
        {[:.tag] (do->
                   (set-attr :onClick #(do (put! select-tag tag) false))
                   (content tag))}))))

(defn note-item [note owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [select-note select-tag]}]
      (kioo/component "templates/list.html" [:ul.list :> :li]
        {[:li] (set-attr :onClick #(put! select-note @note))
         [:.title] (content (jn/title note))
         [:.subtext] (content (jn/summary note))
         [:.tags] (content (om/build-all tag (jn/tags note)
                              {:init-state {:select-tag select-tag}}))
         [:.timestamp] (content (jn/date-str note))}))))

(defn note-list [cursor owner]
  (reify
    om/IInitState
    (init-state [_]
      {:select-note (chan)
       :select-tag (chan)
       :searches (chan)
       :scrolls (chan)})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [actions searches select-note select-tag scrolls]} (om/get-state owner)]
        (go
          (dochan [note select-note]
            (>! actions {:type :select
                         :note note})))
        (go
          (dochan [tag select-tag]
            (om/update! cursor :term tag)))
        (go
          (dochan [term searches]
            (om/update! cursor :term term)))
        (go
          (let [debounced (util/debounce scrolls 100)]
            (dochan [top debounced]
              ; FIXME: this causes an undeeded re-render
              (println "scoll" top)
              (om/update! cursor :scroll top))))))

    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner "scrollable")]
        (set! (.-scrollTop el) (:scroll cursor))))

    om/IRenderState
    (render-state [this {:keys [actions select-note select-tag searches scrolls]}]
      (let [term (:term cursor)
            notes (jn/matching (jn/sorted (vals (:notes cursor))) term)]
        (kioo/component "templates/list.html" [:#list]
          {[:.left :.btn] (set-attr :onClick #(put! actions {:type :settings}))
           [:.right :.btn] (set-attr :onClick #(put! actions {:type :create}))
           [:.search] (substitute (om/build search cursor
                                 {:init-state {:searches searches}
                                  :state {:term term}}))
           [:.wrapper] (set-attr :ref "scrollable"
                                 :onScroll #(put! scrolls (.. % -target -scrollTop)))
           [:ul.list] (content (om/build-all note-item notes
                                 {:init-state {:select-note select-note
                                               :select-tag select-tag}}))})))))

(defn- editable-text [note]
  (let [lines (string/split-lines (:text note))]
    (string/join (map #(str % "<br>") lines))))

(defn- parse-text [e]
  ; TODO: innerText not always supported
  (.. e -target -innerText))

(defn note-editor [note owner]
  (reify
    om/IInitState
    (init-state [_]
      {:updates (chan)
       :title (jn/title note)})

    om/IDidMount
    (did-mount [_]
      (let [{:keys [updates actions]} (om/get-state owner)
            debounced (util/debounce updates 500)]
        (go
          (dochan [updated-note debounced]
            (>! actions {:type :save
                         :note updated-note})))))

    om/IRenderState
    (render-state [this {:keys [actions updates title]}]
      (kioo/component "templates/editor.html" [:#editor]
        {[:.left :.btn] (set-attr :onClick #(put! actions {:type :close}))
         [:.right :.btn] (set-attr :onClick #(put! actions {:type :delete
                                                            :note @note}))
         [:h1] (content title)
         [:.content] (substitute
                       (dom/div #js {:className "content"
                                     :ref "editor"
                                     :contentEditable "true"
                                     :dangerouslySetInnerHTML #js {:__html (editable-text note)}
                                     :onKeyUp #(let [text (parse-text %)
                                                     updated-note (assoc @note :text text)]
                                                 (om/set-state! owner :title (jn/title updated-note))
                                                 (put! updates updated-note))}))}))))

(defn settings [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [actions]}]
      (kioo/component "templates/settings.html" [:#settings]
        {[:.left :.btn] (set-attr :onClick #(put! actions {:type :close}))
         [:.status] (content
                      (str "Dropbox syncing is " (if (:connected cursor) "on" "off") "."))
         [:button] (do->
                     (set-attr :onClick #(put! actions {:type :toggle-connection}))
                     (content
                       (if (:connected cursor) "Disconnect" "Connect")))}))))

(defn root [cursor owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [actions]}]
      (let [route (:route cursor)]
        (case (:name route)
          :note-list (om/build note-list cursor
                       {:init-state {:actions actions}})
          :note-edit (om/build note-editor (:note route)
                       {:init-state {:actions actions}})
          :settings (om/build settings cursor
                      {:init-state {:actions actions}})
          (throw (js/Error. (str "No such route: " (:name route)))))))))
