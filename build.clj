(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def game 'berkelana-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name game) version))

(def basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm]})))
(def basis-repl (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :repl]})))

(defn clean [& _]
  (println "cleaning target...")
  (b/delete {:path "target"}))

(defn compile-java [& _]
  (clean)
  (println "compiling java...")
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis @basis}))

(defn desktop [& _]
  (compile-java)
  (println "running desktop game...")
  (let [cmd (b/java-command {:basis @basis
                             :main  'clojure.main
                             :main-args ["-m" "engine.start"]})]
    (b/process cmd)))

(defn compile-java-repl [& _]
  (clean)
  (println "compiling java for repl...")
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis @basis-repl}))

(defn repl [& _]
  ;; (compile-java-repl)
  (println "running desktop game with repl...")
  (let [cmd (b/java-command {:basis @basis-repl
                             :main  'clojure.main
                             :main-args ["-m" "start-dev"]})]
    (b/process cmd)))

(defn jar [& _]
  (compile-java)
  (b/write-pom {:class-dir class-dir
                :lib game
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))