(ns build
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b])
  (:import
   [java.io PushbackReader]))

(def game 'berkelana-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name game) version))

(def deps-edn (edn/read (PushbackReader. (io/reader "deps.edn"))))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [& _]
  (b/delete {:path "target"}))

(defn compile-java [& _]
  (b/delete {:path "target"})
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir}))

(defn native [& _]
  (compile-java)
  (println "what")
  (let [cmd (b/java-command {:basis @basis
                             :main  'clojure.main
                             :main-args ["-m" "engine.start-dev"]})]
    (b/process cmd)))

(defn jar [& _]
  (compile nil)
  (b/write-pom {:class-dir class-dir
                :lib game
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))