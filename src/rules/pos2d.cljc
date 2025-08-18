(ns rules.pos2d 
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::x number?)
(s/def ::y number?)