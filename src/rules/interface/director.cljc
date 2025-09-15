(ns rules.interface.director 
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::context keyword?)