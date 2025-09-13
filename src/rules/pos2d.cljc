(ns rules.pos2d
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::x number?)
(s/def ::y number?)

(s/def ::pos2d (s/keys :req-un [::x ::y]))