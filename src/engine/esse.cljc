(ns engine.esse 
  (:require [clojure.spec.alpha :as s]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(s/def ::prev-x int?)
(s/def ::prev-y int?)
(s/def ::pos-x int?)
(s/def ::pos-y int?)

(s/def ::x number?)
(s/def ::y number?)

(s/def ::move-duration number?)
(s/def ::move-delay number?)
(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)

(s/def ::sprite-from-asset any?)
(s/def ::current-sprite (s/nilable map?))
(s/def ::frame-index int?)