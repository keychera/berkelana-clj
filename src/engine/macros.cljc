(ns engine.macros
  (:require
   #?@(:clj [[clojure.java.io :as io]
             [tile-soup.core :as ts]])
   [odoyle.rules :as o])
  #?(:clj (:import [java.nio.file Paths])))

(defmacro s->
  "Thread like `->` but always ends with (o/reset!)."
  [x & forms]
  `(-> ~x ~@forms o/reset!))

(defmacro insert!
  "Thread like `->` but always ends with (o/reset!)."
  ([[id attr value]]
   `(s-> ~'session (o/insert ~id ~attr ~value)))
  ([id attr->value]
   `(s-> ~'session (o/insert ~id ~attr->value)))
  ([id attr value]
   `(s-> ~'session (o/insert ~id ~attr ~value))))

(defmacro vars->map [& vars]
  (zipmap (map (comp keyword name) vars) vars))

;; this is loading resource on compile-time, hence cljs can have access to java stuff
#?(:clj (def public-resource-path (Paths/get (.toURI (io/resource "public")))))

#?(:clj (defmacro read-tiled-map-on-compile [fname]
          (let [tiled-path (str "public/" fname)
                tiled-res (io/resource tiled-path)]
            (-> tiled-res slurp ts/parse
                (assoc :path tiled-path 
                       :home-path (str (.relativize public-resource-path (.getParent (Paths/get (.toURI tiled-res))))))
                pr-str))))
