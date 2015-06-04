(ns jot.data
  (:require [reagent.core :as reagent]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :as re-frame :refer [register-handler register-sub]]
            [jot.notes :refer [matches?]]
            [jot.routing :as routing]
            [jot.sync :as sync]
            [jot.util :as util]))

(def initial-state {:current-route [:note-list {}]
                    :search-term ""
                    :scroll-position 0
                    :syncing? false
                    :notes {"1" {:text "Note One\nThis is note one.\n#foo" :timestamp (js/Date.)}
                            "2" {:text "Note Two\nThis is note two.\n#foo #bar" :timestamp (js/Date.)}
                            "3" {:text "Note Three\nThis is note three." :timestamp (js/Date.)}
                            "4" {:text "Note Four\nThis is note four." :timestamp (js/Date.)}
                            "5" {:text "Note Five\nThis is note five.\n#bar" :timestamp (js/Date.)}
                            "6" {:text "Note Six\nThis is note six.\n#baz" :timestamp (js/Date.)}}})

(defn format-notes [notes]
  (for [[id note] notes] (merge note {:id id})))

(defn find-notes [db]
  (->> (:notes db)
       (format-notes)
       (filter #(matches? % (:search-term db)))
       (sort #(compare (:timestamp %2) (:timestamp %1)))))

;; subscriptions

(register-sub
 :current-route
 (fn [db]
   (reaction (:current-route @db))))

(register-sub
 :notes
 (fn [db]
   (reaction (find-notes @db))))

(register-sub
 :note
 (fn [db [_ id]]
   (reaction (get-in @db [:notes id]))))

(register-sub
 :search-term
 (fn [db]
   (reaction (get-in @db [:search-term]))))

(register-sub
 :scroll-position
 (fn [db]
   (reaction (:scroll-position @db))))

(register-sub
 :syncing?
 (fn [db]
   (reaction (:syncing? @db))))

;; handlers

(register-handler
 :initialize
 (fn [db [_ state]]
   (merge db (or state initial-state))))

(register-handler
 :navigate
 (fn [db [_ route]]
   (assoc db :current-route route)))

(register-handler
 :new-note
 (fn [db]
   (let [id (util/make-uuid)
         note {:text "Untitled" :timestamp (js/Date.)}]
     (routing/visit! (routing/note-edit-path {:id id}))
     (assoc-in db [:notes id] note))))

(register-handler
 :update-note
 (fn [db [_ id text]]
   (-> db
       (assoc :scroll-position 0)
       (assoc-in [:notes id :text] text)
       (assoc-in [:notes id :timestamp] (js/Date.)))))

(register-handler
 :delete-note
 (fn [db [_ id]]
   (routing/visit! (routing/note-list-path))
   (assoc db :notes (dissoc (:notes db) id))))

(register-handler
 :search
 (fn [db [_ search-term]]
   (assoc db :search-term search-term)))

(register-handler
 :clear-search
 (fn [db _]
   (assoc db :search-term "")))

(register-handler
 :scroll
 (fn [db [_ scroll-position]]
   (assoc db :scroll-position scroll-position)))

(register-handler
 :toggle-sync
 (fn [{:keys [syncing?] :as db} _]
   (if syncing?
     (sync/disconnect!)
     (sync/connect!))
   (update db :syncing? not)))
