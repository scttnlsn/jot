(defproject jot "0.1.0-SNAPSHOT"
  :url "https://github.com/scttnlsn/jot"

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [cljsjs/dropbox "0.10.3-0"]
                 [re-frame "0.4.1"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.0.6"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {:output-to "build/dev/jot.js"
                                   :output-dir "build/dev"
                                   :optimizations :none
                                   :source-map true}}]})
