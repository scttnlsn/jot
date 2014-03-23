(defproject jot "0.1.0-SNAPSHOT"
  :url "https://github.com/scttnlsn/jot"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [com.facebook/react "0.9.0.1"]
                 [alandipert/storage-atom "1.1.2"]
                 [om "0.5.2"]
                 [secretary "1.0.2"]]

  :plugins [[lein-cljsbuild "1.0.2"]]
  :hooks [leiningen.cljsbuild]

  :source-paths ["src"]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {:output-to "jot.js"
                                   :output-dir "out/dev"
                                   :optimizations :none
                                   :source-map true}}
                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:output-to "assets/jot.js"
                                   :output-dir "out/prod"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["react/react.min.js" "vendor/dropbox.min.js" "vendor/fastclick.js"]
                                   :externs ["react/externs/react.js" "vendor/dropbox.js" "vendor/fastclick.js"]
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]})
