(ns engine.macros
  #?@(:clj [(:require [clojure.string :as str]
                      [clojure.java.io :as io]
                      [tile-soup.core :as ts]
                      [tile-soup.utils]
                      [odoyle.rules :as o])
            (:import
             [java.nio.file Paths])])
  #?(:cljs (:require-macros [engine.macros :refer [s-> insert! vars->map]])))

#?(:clj
    (defmacro s->
     "Thread like `->` but always ends with (o/reset!)."
     [x & forms]
     `(-> ~x ~@forms o/reset!)))

#?(:clj
   (defmacro insert!
     "Thread like `->` but always ends with (o/reset!)."
     ([[id attr value]]
      `(s-> ~'session (o/insert ~id ~attr ~value)))
     ([id attr->value]
      `(s-> ~'session (o/insert ~id ~attr->value)))
     ([id attr value]
      `(s-> ~'session (o/insert ~id ~attr ~value)))))

#?(:clj
   (defmacro vars->map [& vars]
     (zipmap (map (comp keyword name) vars) vars)))

;; this is loading resource on compile-time, hence cljs can have access to java stuff
#?(:clj (def public-resource-path (Paths/get (.toURI (io/resource "public")))))

#?(:clj
   (defn parse-tsx-if-any [home-path content]
     (try
       (let [source (-> content :attrs :source)]
         (if (and (= :tileset (:tag content)) (some-> source (str/ends-with? ".tsx")))
           (assoc content :content
                  [(ts/parse :tile-soup.tileset/tileset (slurp (io/resource (str "public/" home-path "/" source))))])
           content))
       (catch Throwable err
         (println "error on parsing content for tilesets" err)
         content))))

#?(:clj
   (defmacro read-tiled-map-on-compile [fname]
     (let [tiled-path (str "public/" fname)
           tiled-res  (io/resource tiled-path)
           _          (println "[berkelana] compiling" tiled-path)
           home-path  (str (.relativize public-resource-path (.getParent (Paths/get (.toURI tiled-res)))))
           parsed-tiled-res (-> tiled-res slurp ts/parse)]
       (-> parsed-tiled-res
           (as-> data (update data :content (fn [content] (mapv #(parse-tsx-if-any home-path %) content))))
           (assoc :asset-path tiled-path :home-path home-path)
           pr-str))))

