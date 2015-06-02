(ns jot.components
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [dochan]])
  (:require [cljs.core.async :refer [close! chan put!]]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [jot.notes :as notes]
            [jot.routing :as routing]
            [jot.util :as util :refer [debounce]]))

(defn header [left middle right]
  [:header
   [:div.menu
    [:div.left left]
    middle
    [:div.right right]]])

(defn button [name params]
  [:a.btn params
   [:i {:class (str "foundicon-" name)}]])

(defn search-box []
  (let [search-term (subscribe [:search-term])]
    [:div.search
     [:div.clearable
      [:input {:type "text"
               :placeholder "Search..."
               :value @search-term
               :on-change #(dispatch [:search (.. % -target -value)])}]
      [:i.remove.foundicon-remove {:on-click #(dispatch [:clear-search])}]]]))

(defn tag [i name]
  [:span.tag {:key i :on-click #(dispatch [:search name])} name])

(defn note-list-item [i note]
  [:li.palette.gray {:key i}
   [:div.top {:on-click #(routing/visit! (routing/note-edit-path {:id (:id note)}))}
    [:h2.title  (notes/title note)]
    [:span.subtext (notes/summary note)]]
   [:div.bottom.highlight
    [:div.tags.left
     (map-indexed tag (notes/tags note))]
    [:span.timestamp (notes/date-string note)]]])

(defn scrollable [child]
  (let [scroll-ch (chan)]
    (reagent/create-class
     {:component-will-mount
      (fn [this]
        (go
          (dochan [scroll-position (debounce scroll-ch 100)]
                  (dispatch [:scroll scroll-position]))))

      :component-did-mount
      (fn [this]
        (let [scroll-position (subscribe [:scroll-position])]
          (aset (reagent/dom-node this) "scrollTop" @scroll-position)))

      :reagent-render
      (fn []
        [:div.wrapper {:on-scroll #(let [scroll-position (.. % -target -scrollTop)]
                                     (put! scroll-ch scroll-position)
                                     scroll-position)}
         child])})))

(defn note-list []
  (let [notes (subscribe [:notes])]
    [:span.list
     [header
      [button "settings" {:href (routing/settings-path)}]
      [search-box]
      [button "plus" {:on-click #(dispatch [:new-note])}]]
     [:section.scroll
      [scrollable
       [:ul.list
        (map-indexed note-list-item @notes)]]]]))

(defn note-edit [id]
  (let [note (subscribe [:note id])]
    [:span.editor
     [header
      [button "left-arrow" {:href (routing/note-list-path)}]
      [:h1 (:title @note)]
      [button "trash" {:on-click #(dispatch [:delete-note id])}]]
     [:section.scroll
      [:textarea.content {:default-value (:text @note)
                          :on-change #(dispatch-sync [:update-note id (.. % -target -value)])}]]]))

(defn settings []
  [:h1 "fixme"])

;; pages

(defmulti page
  (fn [name _]
    name))

(defmethod page :note-list
  [_ _]
  [note-list])

(defmethod page :note-edit
  [_ {:keys [id]}]
  [note-edit id])

(defmethod page :settings
  [_ _]
  [settings])

;; main component

(defn app []
  (let [current-route (subscribe [:current-route])]
    (apply page @current-route)))
