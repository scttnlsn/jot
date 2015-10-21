(defproject jot "0.1.0-SNAPSHOT"
  :url "https://github.com/scttnlsn/jot"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [cljsjs/dropbox "0.10.3-0"]
                 [cljsjs/fastclick "1.0.6-0"]
                 [re-frame "0.4.1"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.4.1"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :figwheel { :on-jsload "jot.core/render" }
                        :compiler {:output-to "build/dev/jot.js"
                                   :output-dir "build/dev"
                                   :optimizations :none
                                   :source-map true}}

                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:output-to "assets/jot.js"
                                   :output-dir "build/prod"
                                   :optimizations :advanced
                                   :pretty-print false}}]})
