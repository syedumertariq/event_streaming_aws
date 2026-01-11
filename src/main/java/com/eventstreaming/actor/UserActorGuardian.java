package com.eventstreaming.actor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Guardian actor that manages User Actor instances
 */
public class UserActorGuardian extends AbstractBehavior<UserActorGuardian.Command> {
    
    private static final Logger log = LoggerFactory.getLogger(UserActorGuardian.class);
    
    private final Map<String, ActorRef<UserActor.Command>> userActors = new HashMap<>();
    
    // Commands
    public sealed interface Command {}
    
    public record GetOrCreateUserActor(String userId, ActorRef<ActorRef<UserActor.Command>> replyTo) 
        implements Command {}
    
    public static Behavior<Command> create() {
        return Behaviors.setup(UserActorGuardian::new);
    }
    
    private UserActorGuardian(ActorContext<Command> context) {
        super(context);
        log.info("UserActorGuardian started");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetOrCreateUserActor.class, this::getOrCreateUserActor)
            .build();
    }
    
    private Behavior<Command> getOrCreateUserActor(GetOrCreateUserActor command) {
        String userId = command.userId;
        
        ActorRef<UserActor.Command> userActor = userActors.get(userId);
        
        if (userActor == null) {
            String actorName = "user-" + userId.replaceAll("[^a-zA-Z0-9]", "_");
            userActor = getContext().spawn(UserActor.create(userId), actorName);
            userActors.put(userId, userActor);
            log.info("Created new UserActor for user: {}", userId);
        }
        
        command.replyTo.tell(userActor);
        return this;
    }
}