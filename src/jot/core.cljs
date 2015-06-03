(ns jot.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch-sync]]
            [jot.components :as components]
            [jot.data :as data]
            [jot.routing :as routing]))

(enable-console-print!)
(dispatch-sync [:initialize])
(routing/start-history!)

(reagent/render-component [components/app]
                          (js/document.getElementById "app"))
