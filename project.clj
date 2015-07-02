(defproject hap-todo "0.1-SNAPSHOT"
  :description "Example ToDo Service demonstrating HAP."
  :url "https://github.com/alexanderkiel/hap-todo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [prismatic/plumbing "0.4.4"]
                 [http-kit "2.1.18"]
                 [org.clojars.akiel/ring-hap "0.1-SNAPSHOT"]
                 [bidi "1.18.11" :exclusions [org.clojure/clojurescript
                                              com.cemerick/clojurescript.test]]
                 [liberator "0.13"]
                 [pandect "0.5.2"
                  :exclusions [org.bouncycastle/bcprov-jdk15on potemkin]]
                 [environ "1.0.0"]]

  :profiles {:dev
             {:source-paths ["dev"]
              :dependencies [[org.clojure/tools.namespace "0.2.4"]]
              :global-vars {*print-length* 20}}

             :production
             {:main hap-todo.core}})
