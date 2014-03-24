(ns jot.ui
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [dochan]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan]]
            [clojure.string :as string]
            [jot.note :as jn]
            [jot.util :as util]))

; helpers

(defn- button [{:keys [type on-click]}]
  (if (= type :placeholder)
    (dom/span #js {:className "btn placeholder"} nil)
    (dom/a #js {:className "btn" :onClick on-click}
      (dom/i #js {:className (str "foundicon-" type)} nil))))

(defn- header [{:keys [buttons title]}]
  (dom/header nil
    (dom/div #js {:className "menu"}
      (dom/div #js {:className "left"}
        (first buttons))
      title
      (dom/div #js {:className "right"}
        (last buttons)))))

; components

(defn search [_ owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [term searches]}]
      (dom/div #js {:className "search"}
        (dom/span #js {:className "clearable"}
          (dom/input #js {:type "text"
                          :placeholder "Search..."
                          :value term
                          :onChange #(put! searches (.. % -target -value))})
          (dom/i #js {:className "foundicon-remove"
                      :onClick #(put! searches "")}))))))

(defn tag [tag owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [select-tag]}]
      (dom/span #js {:className "tag"
                     :onClick #(do (put! select-tag tag) false)}
        tag))))

(defn note-item [note owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [select-note select-tag]}]
      (dom/li #js {:className "palette gray"
                   :onClick #(put! select-note @note)}
        (dom/div #js {:className "top"}
          (dom/h2 nil
            (jn/title note))
          (dom/span #js {:className "subtext"}
            (jn/summary note)))
        (dom/div #js {:className "bottom highlight"}
          (apply dom/div #js {:className "tags left"}
            (om/build-all tag (jn/tags note)
              {:init-state {:select-tag select-tag}}))
          (dom/span nil
            (jn/date-str note)))))))

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
          (dochan [top scrolls]
            ; FIXME: this causes an undeeded re-render
            (om/update! cursor :scroll top)))))
    
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner "scrollable")]
        (set! (.-scrollTop el) (:scroll cursor))))
    
    om/IRenderState
    (render-state [this {:keys [actions select-note select-tag searches scrolls]}]
      (let [term (:term cursor)
            notes (jn/matching (jn/sorted (vals (:notes cursor))) term)]
        (dom/span nil
          (header {:buttons [(button {:type "settings"
                                      :on-click #(put! actions {:type :settings})})
                             (button {:type "plus"
                                      :on-click #(put! actions {:type :create})})]
                   :title (om/build search cursor
                            {:init-state {:searches searches}
                             :state {:term term}})})
          (dom/div #js {:className "scroll"}
            (dom/div #js {:className "wrapper"
                          :ref "scrollable"
                          :onScroll #(put! scrolls (.. % -target -scrollTop))}
              (dom/div nil
                (apply dom/ul #js {:className "list"}
                  (om/build-all note-item notes
                    {:init-state {:select-note select-note
                                  :select-tag select-tag}}))))))))))

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
      (dom/span nil
        (header {:buttons [(button {:type "left-arrow"
                                    :on-click #(put! actions {:type :close})})
                           (button {:type "trash"
                                    :on-click #(put! actions {:type :delete
                                                              :note @note})})]
                 :title (dom/h1 nil title)})
        (dom/div #js {:className "scroll"}
          (dom/div #js {:className "content"
                        :contentEditable "true"
                        :ref "editor"
                        :onKeyUp #(let [text (parse-text %)
                                        updated-note (assoc @note :text text)]
                                    (om/set-state! owner :title (jn/title updated-note))
                                    (put! updates updated-note))
                        :dangerouslySetInnerHTML #js {:__html (editable-text note)}}))))))

(defn settings [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [actions]}]
      (dom/span nil
        (header {:buttons [(button {:type "left-arrow"
                                    :on-click #(put! actions {:type :close})})
                           (button {:type "placeholder"})]
                 :title (dom/h1 nil "Settings")})
        (dom/div #js {:className "scroll"}
          (dom/div #js {:className "content"}
            (dom/p nil (str "Dropbox syncing is " (if (:connected cursor) "on" "off") "."))
            (dom/br nil nil)
            (dom/button #js {:className "btn"
                             :onClick #(put! actions {:type :toggle-connection})}
              (if (:connected cursor) "Disconnect" "Connect"))))))))

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
