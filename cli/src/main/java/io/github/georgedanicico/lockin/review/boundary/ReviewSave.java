package io.github.georgedanicico.lockin.review.boundary;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.boundary.Input;
import io.github.georgedanicico.lockin.review.control.ReviewRepository;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.Review;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * {@code lockin review save}: reads a review of a completed week from stdin, validates it and saves
 * it, replacing the week's earlier review. Prints the saved review.
 */
@Component
@RegisterReflectionForBinding({ Review.class, AreaScore.class })
class ReviewSave implements Command {

	private final Input input;

	private final ReviewRepository reviews;

	ReviewSave(Input input, ReviewRepository reviews) {
		this.input = input;
		this.reviews = reviews;
	}

	@Override
	public String name() {
		return "review save";
	}

	@Override
	public Review run(ApplicationArguments args) {
		return reviews.save(input.read(new TypeReference<Review>() {
		}));
	}

}
