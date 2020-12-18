(ns event-sourcing-v2.core
  (:require [clojure.core.async :as a]
            [taoensso.timbre :as timbre]))

;; select-new-events should take only a consumer key.
;; it doesn't make sense for it to also accept to and from,
;; since it can just determine which events the given consumer hasn't seen yet.
#_(defn select-new-events
  [event-store consumer-key {:keys [from to]}]
  )

;; accepting a seq or set of event seq-ids might be less complected,
;; but it's less convenient for consumers.
#_(defn mark-events-as-seen
  [event-store consumer-key events]
  )

;; Consumer key is optional, and ensures that a consumer only
;; processes any given event at most once.
;; Topic key is also optional, and ensures
;; that a consumer only receives specific kinds of events,
;; instead of receiving /every/ event.

;; event handler gets the /event/ itself, not just the event's seq id.
;; just like in re-frame, querying the state of the world
;; while acting on an event is separate from reading the event itself.
;; hmm, but what about events that contain a huge amount of data,
;; like a file upload?
;; well, there would be a single consumer that would be interested
;; in receiving events containing that data, so it could save it somewhere.
;; the rest of the system wouldn't want nor need to know about that;
;; it could just receive any subsequent events about the file
;; after it had finished uploading.
;; and we'd need a cap on file size anyway.
;; what about an event requesting to import a huge Excel file containing
;; lots of data?
;; still the same approach.
;; we still have to decide whether or not to accept it (meaning it starts
;; as a command), then actually emit one or more events
;; to cause the file's data to be saved somewhere,
;; and then possibly emit more events as we process the file's contents.

(defn mark-event-as-seen
  [consumer-id event]
  (println "event " event " marked as seen!"))

(defn subscribe!
  [event-source topic-id consumer-id event-handler]
  (let [listener-chan (a/chan 1)]
    (a/sub event-source topic-id listener-chan)
    (a/go-loop []
      (when-let [event (a/<! listener-chan)]
        (event-handler event)
        (mark-event-as-seen consumer-id event)
        (recur)))))

(comment
  (def event-input (a/chan 1))
  (def event-source (a/pub event-input :topic/id))
  (subscribe! event-source :todos :todos-updater (fn [event]
                                                   (case (:event/type event)
                                                     :todo-created (println "creating todo item in database!"))))
  (subscribe! event-source :reporting :batch-report-generator (fn [event]
                                                                (case (:event/type event)
                                                                  :report-requested (println "generating report!"))))
  (a/put! event-input {:topic/id :todos :event/type :todo-created :todo/description "buy groceries"})
  (a/put! event-input {:topic/id :reporting :event/type :report-requested :report/start-date (java.time.LocalDateTime/now) :report/end-date (.plusDays (java.time.LocalDateTime/now) 7)})
  )

;; consumers are responsible for tracking their own state,
;; using this version.
#_(defn subscribe-v2
  [event-store topic-name consumer-fn]
  )

#_(defn make-db-event-consumer
  [jdbc-url event-source consumer-key event-handler]
  (a/go-loop []
    (when-let [event (a/<! event-source)]
      (event-handler event)
      (mark-event-as-seen jdbc-url event))))

;; upon further thought, i don't think consumers should track which
;; events they've seen versus not seen.
;; why?
;; because it could cause duplication in the code.
;; handling it as part of the publish-subscribe feature itself
;; means it gets one single definition in the code
;; and the publish-subscribe manager is responsible for ensuring
;; that each consumer only gets the events that it hasn't seen yet.
;; it means no consumer ever has to add handling for tracking seen messages
;; or throwing away duplicates.

;; this is... not terrible, but we'd need multiple of these:
;; one per consumer.
;; each consumer might need a different function signature
;; because it might need access to different areas of the system,
;; e.g. the database, or Redis, or Sendgrid, or AWS S3, or...
;; think about how re-frame does it. each event handler
;; might need access to different things, and might
;; affect different areas of the system (or the outside world).
#_(defmulti handle-event :type)

#_(defmethod handle-event :default
  [_ event]
  (timbre/warn "ignoring event" event))

(comment
  ;; copying an example from clojuredocs to understand how a/pub
  ;; and a/sub work.
  (defn monitor! [publisher topic-id]
    (let [listener-chan (a/chan 1)]
      (a/sub publisher topic-id listener-chan)
      (a/go-loop []
        (when-let [{:keys [message]} (a/<! listener-chan)]
          (println topic-id \- message)
          (recur)))))
  (def source (a/chan 1))
  (def publisher (a/pub source :topic/id))
  (monitor! publisher :reporting)
  (monitor! publisher :notifications)
  (a/put! source {:topic/id :reporting :message "generate monthly report..."})
  (a/put! source {:topic/id :notifications :message "send outbound email..."})
  (a/put! source {:topic/id :database :message "temp-file cleanup task started..."})
  (a/close! source)
  )
