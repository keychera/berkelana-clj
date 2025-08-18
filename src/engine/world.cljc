(ns engine.world
  (:require
   #?(:cljs [rules.dev.leva-rules :as leva-rules])
   [assets.asset :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [rules.dev.dev-only :as dev-only]
   [rules.grid-move :as grid-move]
   [rules.input :as input]
   [rules.shader :as shader]
   [rules.time :as time]
   [rules.ubim :as ubim]))

(defonce world* (atom nil))

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

(def rules
  (o/ruleset
   {::window
    [:what
     [::window ::width width]
     [::window ::height height]]

    }))

(defonce ^:devonly previous-rules (atom nil))



(defn init-world [session]
  (let [all-rules (concat rules
                          time/rules
                          input/rules
                          asset/rules
                          tiled/rules
                          grid-move/rules
                          shader/rules
                          ubim/rules
                          #?(:cljs leva-rules/rules)
                          dev-only/rules)
        init-only? (nil? session)
        session (if init-only?
                  (o/->session)
                  (->> @previous-rules ;; devonly : refresh rules without resetting facts
                       (map :name)
                       (reduce o/remove-rule session)))]
    (reset! previous-rules all-rules)
    (-> (->> all-rules
             (map #'rules-debugger-wrap-fn)
             (reduce o/add-rule session)))))

;; specs
(s/def ::width number?)
(s/def ::height number?)

(comment
  (o/query-all @world* ::tiled/tilesets-to-load))