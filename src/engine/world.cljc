(ns engine.world
  (:require
   [clojure.spec.alpha :as s]
   [expound.alpha :as expound]
   [odoyle.rules :as o])
  #?(:cljs (:require-macros [engine.world :refer [system]])))

(s/def ::atom* (partial instance? #?(:clj clojure.lang.Atom :cljs Atom)))

;; game system
(s/def ::game map?)
(s/def ::init-fn   fn? #_(fn [game world] world))
(s/def ::reload-fn fn? #_(fn [game world] world))

(s/def ::rule #(instance? odoyle.rules.Rule %))
(expound/defmsg ::rule  "rules must be odoyle.rules/ruleset\n  e.g. (o/ruleset {...})")
(s/def ::rules (s/coll-of ::rule :kind vector?))
(expound/defmsg ::rules "rules must be odoyle.rules/ruleset\n  e.g. (o/ruleset {...})")

(s/def ::system
  (s/keys :req [::rules]
          :opt [::init-fn ::reload-fn]))

#?(:clj
   (defmacro system [name m]
     `(def ~name
        (if (not (s/valid? ::system ~m))
          (throw (ex-info (expound/expound-str ::system ~m) {}))
          ~m))))

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  (when (#{} (:name rule))
                    (println (:name rule) "is comparing" old-fact "=>" new-fact))
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "when" (:name rule)))
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "firing" (:name rule) "for" (select-keys match [:esse-id])))
                  (f session match))
                :then-finally
                (fn [f session]
                  ;; (println :then-finally (:name rule))
                  (f session))}))

(defonce ^:devonly previous-rules (atom nil))

(defn init-world [game world all-rules reload-fns]
  (let [prev-rules* (::prev-rules* game)
        init-only?  (nil? world)
        session     (if init-only?
                      (o/->session)
                       ;; devonly : refresh rules without resetting facts
                      (let [world
                            (reduce (fn [world reload-fn] (reload-fn game world)) world reload-fns)]
                        (->> @prev-rules* ;; devonly : refresh rules without resetting facts
                             (map :name)
                             (reduce o/remove-rule world))))]
    (reset! previous-rules all-rules)
    (-> (->> all-rules
             (map #'rules-debugger-wrap-fn)
             (reduce o/add-rule session))
        (o/insert ::global ::game game))))
