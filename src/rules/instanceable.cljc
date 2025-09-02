(ns rules.instanceable 
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::raw any?)
(s/def ::instanced any?)
