(ns engine.session
  (:require
   [clojure.spec.alpha :as s]
   [engine.esse :as esse]
   [odoyle.rules :as o]))

(defonce session* (atom {}))

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  ;; (println :what (:name rule) new-fact old-fact)
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  ;; (println :when (:name rule) match)
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{::compile-shader ::compiling-shader} (:name rule))
                    (println "firing" (:name rule)))
                  ;; (println :then (:name rule) match)
                  (f session match))
                :then-finally
                (fn [f session]
                  ;; (println :then-finally (:name rule))
                  (f session))}))

(def rules
  (o/ruleset
   {::window
    [:what
     [::window ::width width]
     [::window ::height height]]

    ::mouse
    [:what
     [::mouse ::x x]
     [::mouse ::y y]]

    ::leva-color
    [:what
     [::leva-color ::r r]
     [::leva-color ::g g]
     [::leva-color ::b b]]
    
    ::leva-spritesheet
    [:what
     [::leva-spritesheet ::crop? crop?]
     [::leva-spritesheet ::frame frame-index]]

    ::sprite-esse
    [:what
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::esse/current-sprite current-sprite]]

    ::load-image
    [:what
     [esse-id ::esse/image-to-load image-path]]

    ::loading-image
    [:what
     [esse-id ::esse/image-to-load image-path]
     [esse-id ::esse/loading-image true]
     :then
     (o/retract! esse-id ::esse/image-to-load)]}))

(def initial-session
  (-> (->> rules
           (map #'rules-debugger-wrap-fn)
           (reduce o/add-rule (o/->session)))
      (o/insert :ubim #::esse{:x 150 :y 100 :image-to-load "char0.png"})))


;; specs
(s/def ::total number?)
(s/def ::delta number?)

(s/def ::width number?)
(s/def ::height number?)

(s/def ::x number?)
(s/def ::y number?)

(s/def ::crop? boolean?)
(s/def ::frame int?)

(s/def ::r (s/and number? #(<= 0 % 255)))
(s/def ::g (s/and number? #(<= 0 % 255)))
(s/def ::b (s/and number? #(<= 0 % 255)))