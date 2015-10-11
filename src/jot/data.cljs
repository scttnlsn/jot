(ns jot.data
  (:require [reagent.core :as reagent]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :as re-frame :refer [register-handler register-sub]]
            [jot.notes :refer [matches?]]
            [jot.routing :as routing]
            [jot.sync :as sync]
            [jot.util :as util]))

(def initial-state {:current-route (routing/default-route)
                    :search-term ""
                    :scroll-position 0
                    :syncing? false
                    :notes {}})

(defn all-notes [db]
  (:notes db))

(defn visible-notes [db]
  (->> (all-notes db)
       (map last)
       (filter #(not (:deleted? %)))
       (filter #(matches? % (:search-term db)))
       (sort #(compare (:timestamp %2) (:timestamp %1)))))

(defn dirty-notes [db]
  (->> (all-notes db)
       (map last)
       (filter :dirty?)))

;; subscriptions

(register-sub
  :current-route
  (fn [db]
    (reaction (:current-route @db))))

(register-sub
  :notes
  (fn [db]
    (reaction (visible-notes @db))))

(register-sub
  :dirty-notes
  (fn [db]
    (reaction (dirty-notes @db))))

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
    (merge db initial-state (or state {}))))

(register-handler
  :update-db
  (fn [db [_ state]]
    (merge db state)))

(register-handler
  :navigate
  (fn [db [_ route]]
    (assoc db :current-route route)))

(register-handler
  :new-note
  (fn [db]
    (let [id (util/make-uuid)
          note {:id id
                :text ""
                :timestamp (js/Date.)
                :dirty? true
                :volatile? true}]
      (routing/visit! (routing/note-edit-path note))
      (assoc-in db [:notes id] note))))

(register-handler
  :update-note
  (fn [db [_ id updates]]
    (-> db
        (update-in [:notes id] #(merge % updates)))))

(register-handler
  :update-text
  (fn [db [_ id text]]
    (-> db
        (assoc :scroll-position 0)
        (update-in [:notes id] #(merge % {:text text
                                          :timestamp (js/Date.)
                                          :dirty? true})))))

(register-handler
  :delete-note
  (fn [db [_ id]]
    (routing/visit! (routing/note-index-path))
    (-> db
        (update-in [:notes id] #(merge % {:deleted? true
                                          :dirty? true})))))

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

(register-handler
  :assoc-note
  (fn [db [_ {:keys [id] :as note}]]
    (-> db
        (assoc-in [:notes id] note))))

(register-handler
  :dissoc-note
  (fn [db [_ {:keys [id] :as note}]]
    (-> db
        (util/dissoc-in [:notes id]))))
