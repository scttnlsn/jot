(defproject jot "0.1.0-SNAPSHOT"
  :url "https://github.com/scttnlsn/jot"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [com.facebook/react "0.11.2"]
                 [om "0.7.3"]
                 [kioo "0.4.0"]
                 [prismatic/om-tools "0.3.2"]
                 [secretary "1.2.1"]
                 [tailrecursion/cljson "1.0.7"]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]
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
                                   :preamble ["react/react.min.js" "vendor/dropbox.min.js" "vendor/fastclick.min.js"]
                                   :externs ["react/externs/react.js" "vendor/dropbox.js" "vendor/fastclick.js"]
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]})
