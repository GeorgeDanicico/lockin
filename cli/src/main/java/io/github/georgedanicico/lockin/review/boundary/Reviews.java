package io.github.georgedanicico.lockin.review.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.review.control.ReviewRepository;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.Review;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** {@code lockin reviews [--last=N]}: the last N reviews (default 1, at most 52), newest first. */
@Component
@RegisterReflectionForBinding({ Review.class, AreaScore.class })
class Reviews implements Command {

	private final ReviewRepository reviews;

	Reviews(ReviewRepository reviews) {
		this.reviews = reviews;
	}

	@Override
	public String name() {
		return "reviews";
	}

	@Override
	public List<Review> run(ApplicationArguments args) {
		return reviews.last(Command.option(args, "last").map(Reviews::count).orElse(1));
	}

	private static int count(String value) {
		var count = value.matches("[0-9]{1,2}") ? Integer.parseInt(value) : 0;
		if (count < 1 || count > 52) {
			throw new IllegalArgumentException("--last must be a whole number from 1 to 52: " + value);
		}
		return count;
	}

}
