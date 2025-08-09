(ns rules.input
  (:require
   #?(:clj [engine.macros :refer [s->]]
      :cljs [engine.macros :refer-macros [s->]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::mouse
    [:what
     [::mouse ::x mouse-x]
     [::mouse ::y mouse-y]]

    ::pressed-key
    [:what
     [keyname ::pressed-key ::keydown]]

    ::one-frame-keyup
    [:what
     [keyname ::pressed-key ::keyup]
     :then
     (s-> session (o/retract keyname ::pressed-key))]}))


(s/def ::x number?)
(s/def ::y number?)

(s/def ::pressed-key keyword?)
(s/def ::keydown any?)
(s/def ::keyup any?)

